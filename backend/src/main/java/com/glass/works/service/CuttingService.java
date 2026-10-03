package com.glass.works.service;

import com.glass.works.common.BizException;
import com.glass.works.common.Dict;
import com.glass.works.domain.CuttingOrder;
import com.glass.works.domain.CuttingPiece;
import com.glass.works.domain.GlassStock;
import com.glass.works.dto.CuttingRequests.OrderRequest;
import com.glass.works.dto.CuttingRequests.PieceRequest;
import com.glass.works.dto.CuttingRequests.PreviewRequest;
import com.glass.works.dto.CuttingRequests.PreviewResult;
import com.glass.works.dto.CuttingRequests.Rect;
import com.glass.works.dto.RemnantRankDTO;
import com.glass.works.repository.CuttingOrderRepository;
import com.glass.works.repository.CuttingPieceRepository;
import com.glass.works.repository.GlassStockRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 切割与优化：布局试算、利用率派生、余料计算与切割单生成。 */
@Service
public class CuttingService {

    private final CuttingOrderRepository orderRepository;
    private final CuttingPieceRepository pieceRepository;
    private final GlassStockRepository stockRepository;

    public CuttingService(CuttingOrderRepository orderRepository,
                          CuttingPieceRepository pieceRepository,
                          GlassStockRepository stockRepository) {
        this.orderRepository = orderRepository;
        this.pieceRepository = pieceRepository;
        this.stockRepository = stockRepository;
    }

    public List<CuttingOrder> list() {
        return orderRepository.findAll();
    }

    public Map<String, Object> detail(Long id) {
        CuttingOrder order = orderRepository.findById(id)
                .orElseThrow(() -> new BizException("切割单不存在：" + id));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("order", order);
        result.put("pieces", pieceRepository.findByCuttingOrderIdOrderBySortNoAscIdAsc(id));
        return result;
    }

    /** 试算：不落库，返回利用率、余料与布局矩形 */
    public PreviewResult preview(PreviewRequest request) {
        return buildPreview(request.getStockId(), request.getSourceLengthMm(),
                request.getSourceWidthMm(), request.getPieces(), false);
    }

    @Transactional
    public CuttingOrder create(OrderRequest request) {
        GlassStock stock = stockRepository.findById(request.getStockId())
                .orElseThrow(() -> new BizException("原片批次不存在：" + request.getStockId()));
        if (request.getPieces() == null || request.getPieces().isEmpty()) {
            throw new BizException("出片规格清单不能为空");
        }
        PreviewResult preview = buildPreview(stock.getId(), stock.getLengthMm(), stock.getWidthMm(),
                request.getPieces(), true);
        if (preview.isOverflow()) {
            throw new BizException(preview.getMessage());
        }

        String orderNo = nextOrderNo();
        CuttingOrder order = new CuttingOrder();
        order.setOrderNo(orderNo);
        order.setStockId(stock.getId());
        order.setBatchNo(stock.getBatchNo());
        order.setGlassType(stock.getGlassType());
        order.setLayoutNo(StringUtils.hasText(request.getLayoutNo())
                ? request.getLayoutNo() : "LAY-" + orderNo.substring(orderNo.length() - 4));
        order.setOutputCount(preview.getOutputCount());
        order.setSourceAreaSqm(bd(preview.getSourceAreaSqm(), 3));
        order.setOutputAreaSqm(bd(preview.getOutputAreaSqm(), 3));
        order.setRemnantAreaSqm(bd(preview.getRemnantAreaSqm(), 3));
        order.setRemnantSpec(preview.getRemnantSpec());
        order.setCutter(request.getCutter());
        order.setOperator(request.getOperator());
        order.setStatus(Dict.CUT_OPTIMIZED);
        order.setCreatedAt(LocalDateTime.now());
        // 利用率是派生列：setter 会做 0~100% 边界校验
        order.setUtilizationRate(bd(preview.getUtilizationRate(), 2));
        CuttingOrder saved = orderRepository.save(order);

        int sortNo = 1;
        for (PieceRequest piece : request.getPieces()) {
            CuttingPiece entity = new CuttingPiece();
            entity.setCuttingOrderId(saved.getId());
            entity.setSortNo(sortNo++);
            entity.setLengthMm(piece.getLengthMm());
            entity.setWidthMm(piece.getWidthMm());
            entity.setQuantity(piece.getQuantity());
            entity.setRemark(piece.getRemark());
            entity.setAreaSqm(BigDecimal.valueOf(
                    piece.getLengthMm() * piece.getWidthMm() * piece.getQuantity() / 1_000_000.0)
                    .setScale(4, RoundingMode.HALF_UP));
            pieceRepository.save(entity);
        }
        return saved;
    }

    @Transactional
    public CuttingOrder updateStatus(Long id, String status) {
        if (!Dict.isValidCutStatus(status)) {
            throw new BizException("切割单状态非法：" + status);
        }
        CuttingOrder order = orderRepository.findById(id)
                .orElseThrow(() -> new BizException("切割单不存在：" + id));
        order.setStatus(status);
        return orderRepository.save(order);
    }

    /** 原片余料排行（手写 JPQL） */
    public List<RemnantRankDTO> remnantRank() {
        return orderRepository.remnantRank();
    }

    @Transactional
    public void delete(Long id) {
        if (!orderRepository.existsById(id)) {
            throw new BizException("切割单不存在：" + id);
        }
        pieceRepository.deleteByCuttingOrderId(id);
        orderRepository.deleteById(id);
    }

    // ---------------- 核心：布局试算 ----------------

    private PreviewResult buildPreview(Long stockId, Integer sourceLength, Integer sourceWidth,
                                       List<PieceRequest> pieces, boolean strict) {
        GlassStock stock = null;
        if (stockId != null) {
            stock = stockRepository.findById(stockId).orElse(null);
        }
        Integer lengthMm = stock != null ? stock.getLengthMm() : sourceLength;
        Integer widthMm = stock != null ? stock.getWidthMm() : sourceWidth;
        if (lengthMm == null || widthMm == null || lengthMm <= 0 || widthMm <= 0) {
            throw new BizException("请先选择原片批次或填写原片幅面尺寸");
        }

        PreviewResult result = new PreviewResult();
        result.setBatchNo(stock == null ? null : stock.getBatchNo());
        result.setGlassType(stock == null ? null : stock.getGlassType());
        result.setSourceLengthMm(lengthMm);
        result.setSourceWidthMm(widthMm);
        result.setPieces(pieces == null ? new ArrayList<>() : pieces);

        double sourceArea = lengthMm.doubleValue() * widthMm.doubleValue() / 1_000_000.0;
        result.setSourceAreaSqm(round(sourceArea, 3));

        if (pieces == null || pieces.isEmpty()) {
            result.setRemnantAreaSqm(round(sourceArea, 3));
            result.setRemnantSpec(lengthMm + "×" + widthMm);
            result.getLayout().add(new Rect(0, 0, lengthMm, widthMm, "REMNANT", "整版余料", null));
            return result;
        }

        // 1) 单片规格与幅面校验
        List<double[]> units = new ArrayList<>();
        for (PieceRequest piece : pieces) {
            if (piece.getLengthMm() == null || piece.getWidthMm() == null
                    || piece.getLengthMm() <= 0 || piece.getWidthMm() <= 0) {
                throw new BizException("出片规格长宽必须为正整数");
            }
            if (piece.getQuantity() == null || piece.getQuantity() <= 0) {
                throw new BizException("出片数量必须为正整数");
            }
            double w = piece.getLengthMm().doubleValue();
            double h = piece.getWidthMm().doubleValue();
            boolean fits = w <= lengthMm + 1e-6 && h <= widthMm + 1e-6;
            boolean rotated = false;
            if (!fits && h <= lengthMm + 1e-6 && w <= widthMm + 1e-6) {
                double tmp = w;
                w = h;
                h = tmp;
                fits = true;
                rotated = true;
            }
            if (!fits) {
                throw new BizException("出片规格 " + piece.getLengthMm() + "×" + piece.getWidthMm()
                        + "mm 超出原片幅面 " + lengthMm + "×" + widthMm + "mm");
            }
            for (int i = 0; i < piece.getQuantity(); i++) {
                units.add(new double[]{w, h, rotated ? 1 : 0});
            }
        }

        // 2) 面积合计不得超过原片面积（超出给出超出面积）
        double outputArea = 0;
        int outputCount = 0;
        for (PieceRequest piece : pieces) {
            outputArea += piece.getLengthMm().doubleValue() * piece.getWidthMm().doubleValue()
                    * piece.getQuantity() / 1_000_000.0;
            outputCount += piece.getQuantity();
        }
        result.setOutputAreaSqm(round(outputArea, 3));
        result.setOutputCount(outputCount);
        if (outputArea > sourceArea + 1e-9) {
            double over = outputArea - sourceArea;
            String message = "出片面积合计 " + round(outputArea, 3) + "㎡ 超出原片面积 "
                    + round(sourceArea, 3) + "㎡，超出 " + round(over, 3) + "㎡，请减少出片或换用更大原片";
            result.setOverflow(true);
            result.setMessage(message);
            result.setUtilizationRate(round(outputArea / sourceArea * 100.0, 2));
            result.setUtilizationLevel("LOW");
            result.setRemnantAreaSqm(0.0);
            result.setRemnantSpec("0×0");
            if (strict) {
                throw new BizException(message);
            }
            return result;
        }

        // 3) 按宽度从大到小做行式排布（shelf packing），生成布局矩形
        units.sort(Comparator.<double[]>comparingDouble(u -> -u[1]).thenComparingDouble(u -> -u[0]));
        double cursorX = 0;
        double cursorY = 0;
        double rowHeight = 0;
        for (double[] unit : units) {
            double w = unit[0];
            double h = unit[1];
            if (cursorX + w > lengthMm + 1e-6) {
                if (rowHeight > 0 && lengthMm - cursorX > 1) {
                    result.getLayout().add(new Rect(cursorX, cursorY, lengthMm - cursorX, rowHeight,
                            "REMNANT", "余料 " + fmt(lengthMm - cursorX) + "×" + fmt(rowHeight), null));
                }
                cursorY += rowHeight;
                cursorX = 0;
                rowHeight = 0;
            }
            if (cursorY + h > widthMm + 1e-6) {
                result.setOverflow(true);
                result.setMessage("当前排布方式无法在原片幅面内容纳全部出片，请调整规格顺序或数量");
                if (strict) {
                    throw new BizException(result.getMessage());
                }
                break;
            }
            result.getLayout().add(new Rect(cursorX, cursorY, w, h, "PIECE",
                    fmt(w) + "×" + fmt(h), 1));
            cursorX += w;
            rowHeight = Math.max(rowHeight, h);
        }
        if (rowHeight > 0 && lengthMm - cursorX > 1) {
            result.getLayout().add(new Rect(cursorX, cursorY, lengthMm - cursorX, rowHeight,
                    "REMNANT", "余料 " + fmt(lengthMm - cursorX) + "×" + fmt(rowHeight), null));
        }
        double bottom = cursorY + rowHeight;
        if (widthMm - bottom > 1) {
            result.getLayout().add(new Rect(0, bottom, lengthMm, widthMm - bottom,
                    "REMNANT", "余料 " + lengthMm + "×" + fmt(widthMm - bottom), null));
        }

        // 4) 利用率与余料
        double rate = outputArea / sourceArea * 100.0;
        result.setUtilizationRate(round(rate, 2));
        result.setUtilizationLevel(rate < Dict.UTIL_WARN ? "LOW" : (rate > Dict.UTIL_GOOD ? "HIGH" : "MID"));
        result.setRemnantAreaSqm(round(sourceArea - outputArea, 3));
        Rect biggest = null;
        for (Rect rect : result.getLayout()) {
            if (!"REMNANT".equals(rect.getType())) {
                continue;
            }
            if (biggest == null || rect.getW() * rect.getH() > biggest.getW() * biggest.getH()) {
                biggest = rect;
            }
        }
        result.setRemnantSpec(biggest == null ? "0×0" : fmt(biggest.getW()) + "×" + fmt(biggest.getH()));
        result.setMessage(result.isOverflow() ? result.getMessage()
                : "利用率 " + result.getUtilizationRate() + "%，余料 " + result.getRemnantSpec() + "mm");
        return result;
    }

    private String nextOrderNo() {
        String prefix = "CUT" + LocalDateTime.now().format(
                java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
        long seq = orderRepository.count() + 1;
        String orderNo = prefix + String.format("-%03d", seq);
        while (orderRepository.existsByOrderNo(orderNo)) {
            seq++;
            orderNo = prefix + String.format("-%03d", seq);
        }
        return orderNo;
    }

    private static BigDecimal bd(double value, int scale) {
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP);
    }

    private static double round(double value, int scale) {
        double factor = Math.pow(10, scale);
        return Math.round(value * factor) / factor;
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    /** 尺寸展示：整数不带小数，带小数的保留一位 */
    private static String fmt(double value) {
        double v = round1(value);
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
    }
}
