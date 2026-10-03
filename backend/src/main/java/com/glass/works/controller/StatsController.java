package com.glass.works.controller;

import com.glass.works.common.ApiResponse;
import com.glass.works.dto.DefectTypeStatDTO;
import com.glass.works.dto.EdgeStatDTO;
import com.glass.works.dto.FurnaceStatDTO;
import com.glass.works.dto.GlassTypeStatDTO;
import com.glass.works.dto.ProcessStatDTO;
import com.glass.works.dto.ProcessVerdictDTO;
import com.glass.works.dto.RackStatDTO;
import com.glass.works.dto.RemnantRankDTO;
import com.glass.works.service.StatsService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 报表统计：按玻璃种类产量面积、按工序合格 / 返修 / 报废、原片余料排行等 */
@RestController
@RequestMapping("/api/stats")
public class StatsController {

    private final StatsService service;

    public StatsController(StatsService service) {
        this.service = service;
    }

    @GetMapping("/overview")
    public ApiResponse<Map<String, Object>> overview() {
        return ApiResponse.success(service.overview());
    }

    /** 按玻璃种类统计产量与面积 */
    @GetMapping("/glass-type")
    public ApiResponse<List<GlassTypeStatDTO>> byGlassType() {
        return ApiResponse.success(service.byGlassType());
    }

    /** 按工序统计合格 / 返修 / 报废 */
    @GetMapping("/verdict")
    public ApiResponse<List<ProcessVerdictDTO>> verdictByProcess() {
        return ApiResponse.success(service.verdictByProcess());
    }

    /** 原片余料排行 */
    @GetMapping("/remnant")
    public ApiResponse<List<RemnantRankDTO>> remnantRank() {
        return ApiResponse.success(service.remnantRank());
    }

    @GetMapping("/edge")
    public ApiResponse<List<EdgeStatDTO>> byEdgeType() {
        return ApiResponse.success(service.byEdgeType());
    }

    @GetMapping("/furnace")
    public ApiResponse<List<FurnaceStatDTO>> byFurnace() {
        return ApiResponse.success(service.byFurnace());
    }

    @GetMapping("/deep")
    public ApiResponse<List<ProcessStatDTO>> byDeepProcess() {
        return ApiResponse.success(service.byDeepProcess());
    }

    @GetMapping("/defect")
    public ApiResponse<List<DefectTypeStatDTO>> byDefectType() {
        return ApiResponse.success(service.byDefectType());
    }

    @GetMapping("/rack")
    public ApiResponse<List<RackStatDTO>> byShipStatus() {
        return ApiResponse.success(service.byShipStatus());
    }
}
