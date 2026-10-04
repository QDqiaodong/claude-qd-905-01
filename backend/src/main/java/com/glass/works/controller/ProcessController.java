package com.glass.works.controller;

import com.glass.works.common.ApiResponse;
import com.glass.works.domain.EdgeGrinding;
import com.glass.works.domain.TemperingBatch;
import com.glass.works.dto.EdgeStatDTO;
import com.glass.works.dto.FurnaceStatDTO;
import com.glass.works.dto.Requests.GrindingRequest;
import com.glass.works.dto.TemperingRequest;
import com.glass.works.dto.Views.FurnaceVO;
import com.glass.works.service.ProcessService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 磨边与钢化 */
@RestController
@RequestMapping("/api/process")
public class ProcessController {

    private final ProcessService service;

    public ProcessController(ProcessService service) {
        this.service = service;
    }

    // ---------------- 磨边 ----------------

    @GetMapping("/grinding")
    public ApiResponse<List<EdgeGrinding>> grindingList() {
        return ApiResponse.success(service.grindingList());
    }

    /** 磨边记录：进给速度需在边型允许区间内 */
    @PostMapping("/grinding")
    public ApiResponse<EdgeGrinding> createGrinding(@RequestBody GrindingRequest request) {
        return ApiResponse.success("磨边记录已保存", service.createGrinding(request));
    }

    @GetMapping("/grinding/stat")
    public ApiResponse<List<EdgeStatDTO>> grindingStat() {
        return ApiResponse.success(service.grindingStat());
    }

    // ---------------- 钢化 ----------------

    @GetMapping("/tempering")
    public ApiResponse<List<TemperingBatch>> temperingList() {
        return ApiResponse.success(service.temperingList());
    }

    /** 当炉视图：分段温度、时长与破碎率 */
    @GetMapping("/tempering/{id}")
    public ApiResponse<FurnaceVO> furnace(@PathVariable Long id) {
        return ApiResponse.success(service.furnace(id));
    }

    /** 新建钢化批次：加热温度必须在 620~720℃ */
    @PostMapping("/tempering")
    public ApiResponse<TemperingBatch> createTempering(@RequestBody TemperingRequest request) {
        return ApiResponse.success("钢化批次已建立", service.createTempering(request));
    }

    /** 调整工艺参数（滑块） */
    @PutMapping("/tempering/{id}")
    public ApiResponse<TemperingBatch> updateTempering(@PathVariable Long id,
                                                       @RequestBody TemperingRequest request) {
        return ApiResponse.success("工艺参数已更新", service.updateTempering(id, request));
    }

    /** 炉内推进：加热段 → 淬冷段 → 冷却段 → 完成；已完成的批次不可再推进 */
    @PostMapping("/tempering/{id}/advance")
    public ApiResponse<FurnaceVO> advance(@PathVariable Long id) {
        FurnaceVO vo = service.advance(id);
        String message = "已推进到「" + vo.getStageName() + "」";
        if (vo.getNotice() != null) {
            message = vo.getNotice();
        }
        return ApiResponse.success(message, vo);
    }

    /** 出炉登记：出炉结果只允许首次确认一次，重复提交不同片数返回 409 且原结果保留 */
    @PostMapping("/tempering/{id}/discharge")
    public ApiResponse<FurnaceVO> discharge(@PathVariable Long id,
                                            @RequestParam Integer outputCount,
                                            @RequestParam(required = false) Integer brokenCount) {
        FurnaceVO vo = service.discharge(id, outputCount, brokenCount);
        String message = vo.getNotice() != null ? vo.getNotice() : "出炉已登记";
        return ApiResponse.success(message, vo);
    }

    @GetMapping("/furnace-stat")
    public ApiResponse<List<FurnaceStatDTO>> furnaceStat() {
        return ApiResponse.success(service.furnaceStat());
    }
}
