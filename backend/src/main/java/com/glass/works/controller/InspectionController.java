package com.glass.works.controller;

import com.glass.works.common.ApiResponse;
import com.glass.works.domain.FinishedRack;
import com.glass.works.domain.InspectionRecord;
import com.glass.works.dto.DefectTypeStatDTO;
import com.glass.works.dto.InspectionRequest;
import com.glass.works.dto.RackStatDTO;
import com.glass.works.dto.Requests.RackLoadRequest;
import com.glass.works.dto.Requests.RackRequest;
import com.glass.works.dto.Views.DefectMapVO;
import com.glass.works.service.InspectionService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 检验与成品架 */
@RestController
@RequestMapping("/api/inspection")
public class InspectionController {

    private final InspectionService service;

    public InspectionController(InspectionService service) {
        this.service = service;
    }

    @GetMapping("/list")
    public ApiResponse<List<InspectionRecord>> list(@RequestParam(required = false) String processType) {
        return ApiResponse.success(service.list(processType));
    }

    @PostMapping
    public ApiResponse<InspectionRecord> create(@RequestBody InspectionRequest request) {
        return ApiResponse.success("检验记录已保存", service.create(request));
    }

    /** 缺陷分布图：玻璃板平面打点 + 类型占比 */
    @GetMapping("/defect-map")
    public ApiResponse<DefectMapVO> defectMap(@RequestParam(required = false) String processType) {
        return ApiResponse.success(service.defectMap(processType));
    }

    @GetMapping("/defect-stat")
    public ApiResponse<List<DefectTypeStatDTO>> defectStat() {
        return ApiResponse.success(service.defectStat());
    }

    // ---------------- 成品架 ----------------

    @GetMapping("/racks")
    public ApiResponse<List<FinishedRack>> racks() {
        return ApiResponse.success(service.racks());
    }

    @PostMapping("/racks")
    public ApiResponse<FinishedRack> createRack(@RequestBody RackRequest request) {
        return ApiResponse.success("成品架已建立", service.createRack(request));
    }

    /** 成品上架：超过承重上限不允许再上架，并给出剩余承重 */
    @PostMapping("/racks/{id}/load")
    public ApiResponse<FinishedRack> loadRack(@PathVariable Long id,
                                              @RequestBody(required = false) RackLoadRequest request) {
        return ApiResponse.success("成品已上架", service.loadRack(id, request));
    }

    /** 行内发货 */
    @PostMapping("/racks/{id}/ship")
    public ApiResponse<FinishedRack> ship(@PathVariable Long id) {
        return ApiResponse.success("已发货", service.ship(id));
    }

    @GetMapping("/rack-stat")
    public ApiResponse<List<RackStatDTO>> rackStat() {
        return ApiResponse.success(service.rackStat());
    }
}
