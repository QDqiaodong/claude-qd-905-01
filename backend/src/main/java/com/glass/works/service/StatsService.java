package com.glass.works.service;

import com.glass.works.dto.DefectTypeStatDTO;
import com.glass.works.dto.EdgeStatDTO;
import com.glass.works.dto.FurnaceStatDTO;
import com.glass.works.dto.GlassTypeStatDTO;
import com.glass.works.dto.ProcessStatDTO;
import com.glass.works.dto.ProcessVerdictDTO;
import com.glass.works.dto.RackStatDTO;
import com.glass.works.dto.RemnantRankDTO;
import com.glass.works.repository.CuttingOrderRepository;
import com.glass.works.repository.DeepProcessRepository;
import com.glass.works.repository.EdgeGrindingRepository;
import com.glass.works.repository.FinishedRackRepository;
import com.glass.works.repository.GlassStockRepository;
import com.glass.works.repository.InspectionRecordRepository;
import com.glass.works.repository.ReworkBatchRepository;
import com.glass.works.repository.TemperingBatchRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/** 报表统计：全部走手写 JPQL 的投影查询。 */
@Service
public class StatsService {

    private final GlassStockRepository stockRepository;
    private final CuttingOrderRepository cuttingOrderRepository;
    private final EdgeGrindingRepository grindingRepository;
    private final TemperingBatchRepository temperingRepository;
    private final DeepProcessRepository deepProcessRepository;
    private final InspectionRecordRepository inspectionRepository;
    private final FinishedRackRepository rackRepository;
    private final ReworkBatchRepository reworkBatchRepository;
    private final StringRedisTemplate redisTemplate;

    public StatsService(GlassStockRepository stockRepository,
                        CuttingOrderRepository cuttingOrderRepository,
                        EdgeGrindingRepository grindingRepository,
                        TemperingBatchRepository temperingRepository,
                        DeepProcessRepository deepProcessRepository,
                        InspectionRecordRepository inspectionRepository,
                        FinishedRackRepository rackRepository,
                        ReworkBatchRepository reworkBatchRepository,
                        StringRedisTemplate redisTemplate) {
        this.stockRepository = stockRepository;
        this.cuttingOrderRepository = cuttingOrderRepository;
        this.grindingRepository = grindingRepository;
        this.temperingRepository = temperingRepository;
        this.deepProcessRepository = deepProcessRepository;
        this.inspectionRepository = inspectionRepository;
        this.rackRepository = rackRepository;
        this.reworkBatchRepository = reworkBatchRepository;
        this.redisTemplate = redisTemplate;
    }

    /** 手写 JPQL ①：按玻璃种类统计产量与面积 */
    public List<GlassTypeStatDTO> byGlassType() {
        return stockRepository.statByGlassType();
    }

    /** 手写 JPQL ②：原片余料排行 */
    public List<RemnantRankDTO> remnantRank() {
        return cuttingOrderRepository.remnantRank();
    }

    /** 手写 JPQL ③：按工序统计合格 / 返修 / 报废 */
    public List<ProcessVerdictDTO> verdictByProcess() {
        return inspectionRepository.statVerdictByProcess();
    }

    public List<EdgeStatDTO> byEdgeType() {
        return grindingRepository.statByEdgeType();
    }

    public List<FurnaceStatDTO> byFurnace() {
        return temperingRepository.statByFurnace();
    }

    public List<ProcessStatDTO> byDeepProcess() {
        return deepProcessRepository.statByProcessType();
    }

    public List<DefectTypeStatDTO> byDefectType() {
        return inspectionRepository.statByDefectType();
    }

    public List<RackStatDTO> byShipStatus() {
        return rackRepository.statByShipStatus();
    }

    /** 首页概览：各模块台账数量与关键指标 */
    public Map<String, Object> overview() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("stockCount", stockRepository.count());
        data.put("cuttingCount", cuttingOrderRepository.count());
        data.put("grindingCount", grindingRepository.count());
        data.put("temperingCount", temperingRepository.count());
        data.put("deepProcessCount", deepProcessRepository.count());
        data.put("inspectionCount", inspectionRepository.count());
        data.put("rackCount", rackRepository.count());
        data.put("passCount", inspectionRepository.countByVerdict("PASS"));
        data.put("reworkCount", inspectionRepository.countByVerdict("REWORK"));
        data.put("scrapCount", inspectionRepository.countByVerdict("SCRAP"));
        // 返修闭环共享状态：未闭环（隔离 + 处理中 + 待复检）批次数
        long reworkOpen = reworkBatchRepository.countByStatus("ISOLATED")
                + reworkBatchRepository.countByStatus("PROCESSING")
                + reworkBatchRepository.countByStatus("PENDING_RECHECK");
        data.put("reworkOpenCount", reworkOpen);
        data.put("reworkReleasedCount", reworkBatchRepository.countByStatus("RELEASED"));
        data.put("glassTypeStats", byGlassType());
        data.put("remnantRank", remnantRank());
        data.put("verdictByProcess", verdictByProcess());
        data.put("reportViews", bumpReportViews());
        return data;
    }

    /** 报表查看次数记在 Redis 里（有 Redis 就计数，拿不到就返回 -1，不影响主流程） */
    private long bumpReportViews() {
        try {
            return redisTemplate.opsForValue().increment("glass-works:report-views");
        } catch (Exception e) {
            return -1L;
        }
    }
}
