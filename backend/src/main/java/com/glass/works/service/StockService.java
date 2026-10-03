package com.glass.works.service;

import com.glass.works.common.BizException;
import com.glass.works.common.Dict;
import com.glass.works.domain.GlassStock;
import com.glass.works.domain.StorageLocation;
import com.glass.works.dto.Requests.BlockVO;
import com.glass.works.dto.Requests.LevelVO;
import com.glass.works.dto.Requests.MoveRequest;
import com.glass.works.dto.Requests.RackVO;
import com.glass.works.dto.Requests.WarehouseVO;
import com.glass.works.dto.Requests.ZoneVO;
import com.glass.works.dto.StockRequest;
import com.glass.works.repository.GlassStockRepository;
import com.glass.works.repository.StorageLocationRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 原片与库位：批次管理、立体库位视图、上架承重校验。 */
@Service
public class StockService {

    private final GlassStockRepository stockRepository;
    private final StorageLocationRepository locationRepository;

    public StockService(GlassStockRepository stockRepository, StorageLocationRepository locationRepository) {
        this.stockRepository = stockRepository;
        this.locationRepository = locationRepository;
    }

    public List<GlassStock> list() {
        return stockRepository.findAll();
    }

    public List<GlassStock> filter(String glassType, Integer thickness) {
        return stockRepository.filter(StringUtils.hasText(glassType) ? glassType : null, thickness);
    }

    public GlassStock get(Long id) {
        return stockRepository.findById(id)
                .orElseThrow(() -> new BizException("原片批次不存在：" + id));
    }

    @Transactional
    public GlassStock create(StockRequest request) {
        validate(request);
        if (stockRepository.existsByBatchNo(request.getBatchNo())) {
            throw new BizException("批次号已存在：" + request.getBatchNo());
        }
        GlassStock stock = new GlassStock();
        apply(stock, request);
        // 默认值放在 create 里，实体字段不带默认值，避免 PUT 局部更新被刷回
        if (stock.getInboundDate() == null) {
            stock.setInboundDate(LocalDate.now());
        }
        if (stock.getTransmittance() == null) {
            stock.setTransmittance(Dict.glassTypeTransmittance(stock.getGlassType()));
        }
        stock.setWeightKg(stock.getEstimatedWeightKg());
        GlassStock saved = stockRepository.save(stock);
        if (saved.getLocationId() != null) {
            occupyLocation(saved.getLocationId(), saved.getWeightKg(), 1, saved.getSheetCount());
        }
        return saved;
    }

    @Transactional
    public GlassStock update(Long id, StockRequest request) {
        GlassStock stock = get(id);
        Long oldLocationId = stock.getLocationId();
        Integer oldSheets = stock.getSheetCount();
        BigDecimal oldWeight = nvl(stock.getWeightKg());

        if (request.getGlassType() != null && !Dict.isValidGlassType(request.getGlassType())) {
            throw new BizException("玻璃种类非法：" + request.getGlassType());
        }
        if (request.getThicknessMm() != null && !Dict.isValidThickness(request.getThicknessMm())) {
            throw new BizException("厚度不在允许系列内：" + request.getThicknessMm() + "mm");
        }
        applyPartial(stock, request);
        stock.setWeightKg(stock.getEstimatedWeightKg());

        GlassStock saved = stockRepository.save(stock);
        // 库位变了：先释放旧库位，再占用新库位
        if (oldLocationId != null && !oldLocationId.equals(saved.getLocationId())) {
            releaseLocation(oldLocationId, oldWeight, 1, oldSheets);
        }
        if (saved.getLocationId() != null && !saved.getLocationId().equals(oldLocationId)) {
            occupyLocation(saved.getLocationId(), saved.getWeightKg(), 1, saved.getSheetCount());
        }
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        GlassStock stock = get(id);
        if (stock.getLocationId() != null) {
            releaseLocation(stock.getLocationId(), nvl(stock.getWeightKg()), 1, stock.getSheetCount());
        }
        stockRepository.deleteById(id);
    }

    /** 原片上架到某个库位（承重超限要给出剩余承重） */
    @Transactional
    public GlassStock move(Long id, MoveRequest request) {
        GlassStock stock = get(id);
        if (request == null || request.getLocationId() == null) {
            throw new BizException("请选择目标库位");
        }
        StorageLocation target = locationRepository.findById(request.getLocationId())
                .orElseThrow(() -> new BizException("库位不存在：" + request.getLocationId()));
        BigDecimal weight = nvl(stock.getWeightKg());
        if (weight.doubleValue() <= 0) {
            weight = stock.getEstimatedWeightKg();
            stock.setWeightKg(weight);
        }
        BigDecimal remaining = target.getRemainingLoadKg();
        if (weight.compareTo(remaining) > 0) {
            throw new BizException("库位 " + target.getRackCode() + "-" + target.getLevelNo()
                    + " 层承重不足：本批 " + weight.setScale(2, RoundingMode.HALF_UP) + "kg，"
                    + "仅剩 " + remaining.setScale(2, RoundingMode.HALF_UP) + "kg 可用");
        }
        if (stock.getLocationId() != null) {
            releaseLocation(stock.getLocationId(), weight, 1, stock.getSheetCount());
        }
        stock.setLocationId(target.getId());
        occupyLocation(target.getId(), weight, 1, stock.getSheetCount());
        return stockRepository.save(stock);
    }

    /** 立体库位视图：区 → 架 → 层 → 原片批次块 */
    public WarehouseVO warehouse() {
        List<StorageLocation> locations = locationRepository.findAllByOrderByZoneCodeAscRackCodeAscLevelNoDesc();
        Map<Long, List<GlassStock>> byLocation = new LinkedHashMap<>();
        for (GlassStock stock : stockRepository.findAll()) {
            if (stock.getLocationId() != null) {
                byLocation.computeIfAbsent(stock.getLocationId(), k -> new ArrayList<>()).add(stock);
            }
        }

        WarehouseVO vo = new WarehouseVO();
        Map<String, ZoneVO> zoneMap = new LinkedHashMap<>();
        Map<String, RackVO> rackMap = new LinkedHashMap<>();
        for (StorageLocation loc : locations) {
            ZoneVO zone = zoneMap.computeIfAbsent(loc.getZoneCode(), code -> {
                ZoneVO z = new ZoneVO();
                z.setZoneCode(code);
                z.setZoneName(loc.getZoneName());
                return z;
            });
            String rackKey = loc.getZoneCode() + "#" + loc.getRackCode();
            RackVO rack = rackMap.get(rackKey);
            if (rack == null) {
                rack = new RackVO();
                rack.setRackCode(loc.getRackCode());
                rackMap.put(rackKey, rack);
                zone.getRacks().add(rack);
            }
            LevelVO level = new LevelVO();
            level.setId(loc.getId());
            level.setLevelNo(loc.getLevelNo());
            level.setBatchCount(loc.getBatchCount());
            level.setSheetCount(loc.getSheetCount());
            level.setMaxLoadKg(loc.getMaxLoadKg());
            level.setCurrentLoadKg(loc.getCurrentLoadKg());
            level.setRemainingLoadKg(loc.getRemainingLoadKg());
            level.setLoadRate(loc.getLoadRate());
            level.setNote(loc.getNote());
            for (GlassStock stock : byLocation.getOrDefault(loc.getId(), List.of())) {
                level.getBlocks().add(toBlock(stock));
            }
            rack.getLevels().add(level);
        }
        vo.getZones().addAll(zoneMap.values());

        long sheetTotal = 0;
        double areaTotal = 0;
        for (GlassStock stock : stockRepository.findAll()) {
            sheetTotal += stock.getSheetCount() == null ? 0 : stock.getSheetCount();
            areaTotal += stock.getTotalAreaSqm();
        }
        vo.setStockTotal(stockRepository.count());
        vo.setSheetTotal(sheetTotal);
        vo.setAreaTotal(Math.round(areaTotal * 100.0) / 100.0);
        return vo;
    }

    /** 规格串解析演示：把 “6mm 2440×1830” 反解到实体的厚度 / 长 / 宽 */
    @Transactional
    public GlassStock applySpecText(Long id, String specText) {
        GlassStock stock = get(id);
        stock.setSpecText(specText);
        stock.setWeightKg(stock.getEstimatedWeightKg());
        return stockRepository.save(stock);
    }

    private BlockVO toBlock(GlassStock stock) {
        BlockVO block = new BlockVO();
        block.setId(stock.getId());
        block.setBatchNo(stock.getBatchNo());
        block.setGlassType(stock.getGlassType());
        block.setGlassTypeName(Dict.glassTypeName(stock.getGlassType()));
        block.setColor(Dict.glassTypeColor(stock.getGlassType()));
        block.setThicknessMm(stock.getThicknessMm());
        block.setLengthMm(stock.getLengthMm());
        block.setWidthMm(stock.getWidthMm());
        block.setSheetCount(stock.getSheetCount());
        block.setSupplier(stock.getSupplier());
        block.setTransmittance(stock.getTransmittance());
        block.setInboundDate(stock.getInboundDate() == null ? null : stock.getInboundDate().toString());
        block.setSpecText(stock.getSpecText());
        block.setTotalAreaSqm(stock.getTotalAreaSqm());
        block.setWeightKg(stock.getWeightKg());
        block.setLevelId(stock.getLocationId());
        return block;
    }

    private void validate(StockRequest request) {
        if (!StringUtils.hasText(request.getBatchNo())) {
            throw new BizException("批次号不能为空");
        }
        if (!Dict.isValidGlassType(request.getGlassType())) {
            throw new BizException("玻璃种类非法：" + request.getGlassType());
        }
        if (!Dict.isValidThickness(request.getThicknessMm())) {
            throw new BizException("厚度不在允许系列内（4/5/6/8/10/12/15/19 mm）：" + request.getThicknessMm());
        }
        if (request.getLengthMm() == null || request.getLengthMm() <= 0
                || request.getWidthMm() == null || request.getWidthMm() <= 0) {
            throw new BizException("原片规格长宽必须为正整数");
        }
        if (request.getSheetCount() == null || request.getSheetCount() <= 0) {
            throw new BizException("片数必须为正整数");
        }
        if (request.getTransmittance() != null) {
            double t = request.getTransmittance().doubleValue();
            if (t < 0 || t > 100) {
                throw new BizException("透光率必须在 0~100% 之间");
            }
        }
    }

    private void apply(GlassStock stock, StockRequest request) {
        stock.setBatchNo(request.getBatchNo());
        stock.setGlassType(request.getGlassType());
        stock.setThicknessMm(request.getThicknessMm());
        stock.setLengthMm(request.getLengthMm());
        stock.setWidthMm(request.getWidthMm());
        stock.setSheetCount(request.getSheetCount());
        stock.setSupplier(request.getSupplier());
        stock.setTransmittance(request.getTransmittance());
        stock.setInboundDate(request.getInboundDate());
        stock.setLocationId(request.getLocationId());
    }

    private void applyPartial(GlassStock stock, StockRequest request) {
        ifNotNull(request.getBatchNo(), stock::setBatchNo);
        ifNotNull(request.getGlassType(), stock::setGlassType);
        ifNotNull(request.getThicknessMm(), stock::setThicknessMm);
        ifNotNull(request.getLengthMm(), stock::setLengthMm);
        ifNotNull(request.getWidthMm(), stock::setWidthMm);
        ifNotNull(request.getSheetCount(), stock::setSheetCount);
        ifNotNull(request.getSupplier(), stock::setSupplier);
        ifNotNull(request.getTransmittance(), stock::setTransmittance);
        ifNotNull(request.getInboundDate(), stock::setInboundDate);
        ifNotNull(request.getLocationId(), stock::setLocationId);
    }

    private static <T> void ifNotNull(T value, Consumer<T> setter) {
        if (value != null) {
            setter.accept(value);
        }
    }

    private void occupyLocation(Long locationId, BigDecimal weight, int batches, Integer sheets) {
        StorageLocation loc = locationRepository.findById(locationId).orElse(null);
        if (loc == null) {
            return;
        }
        loc.setCurrentLoadKg(nvl(loc.getCurrentLoadKg()).add(nvl(weight)));
        loc.setBatchCount(nul(loc.getBatchCount()) + batches);
        loc.setSheetCount(nul(loc.getSheetCount()) + nul(sheets));
        locationRepository.save(loc);
    }

    private void releaseLocation(Long locationId, BigDecimal weight, int batches, Integer sheets) {
        StorageLocation loc = locationRepository.findById(locationId).orElse(null);
        if (loc == null) {
            return;
        }
        loc.setCurrentLoadKg(nvl(loc.getCurrentLoadKg()).subtract(nvl(weight)).max(BigDecimal.ZERO));
        loc.setBatchCount(Math.max(0, nul(loc.getBatchCount()) - batches));
        loc.setSheetCount(Math.max(0, nul(loc.getSheetCount()) - nul(sheets)));
        locationRepository.save(loc);
    }

    private static BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static int nul(Integer value) {
        return value == null ? 0 : value;
    }
}
