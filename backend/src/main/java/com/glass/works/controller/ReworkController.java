package com.glass.works.controller;

import com.glass.works.common.ApiResponse;
import com.glass.works.domain.ReworkBatch;
import com.glass.works.dto.Requests.RecheckRequest;
import com.glass.works.dto.Requests.ReworkActionRequest;
import com.glass.works.dto.Views.ReworkDetailVO;
import com.glass.works.service.ReworkService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 返修闭环：隔离 → 处理中 → 待复检 → 已放行 */
@RestController
@RequestMapping("/api/rework")
public class ReworkController {

    private final ReworkService service;

    public ReworkController(ReworkService service) {
        this.service = service;
    }

    /** 返修批次列表（可按状态过滤待办） */
    @GetMapping("/list")
    public ApiResponse<List<ReworkBatch>> list(@RequestParam(required = false) String status) {
        return ApiResponse.success(service.list(status));
    }

    /** 各状态待办数量 */
    @GetMapping("/summary")
    public ApiResponse<Map<String, Long>> summary() {
        return ApiResponse.success(service.summary());
    }

    /** 批次时间线：批次 + 事件 + 归并的检验记录 */
    @GetMapping("/{id}")
    public ApiResponse<ReworkDetailVO> detail(@PathVariable Long id) {
        return ApiResponse.success(service.detail(id));
    }

    /** 登记处置：隔离 → 处理中 */
    @PostMapping("/{id}/dispose")
    public ApiResponse<ReworkBatch> dispose(@PathVariable Long id,
                                            @RequestBody(required = false) ReworkActionRequest request) {
        return ApiResponse.success("已登记处置，批次进入处理中", service.dispose(id, request));
    }

    /** 处置完成，提交复检：处理中 → 待复检 */
    @PostMapping("/{id}/submit-recheck")
    public ApiResponse<ReworkBatch> submitRecheck(@PathVariable Long id,
                                                  @RequestBody(required = false) ReworkActionRequest request) {
        return ApiResponse.success("已提交复检，等待复检判定", service.submitRecheck(id, request));
    }

    /** 复检判定：合格 → 已放行；再判返修 → 回到隔离。并发相反结论只生效一个（409 冲突） */
    @PostMapping("/{id}/recheck")
    public ApiResponse<ReworkBatch> recheck(@PathVariable Long id,
                                            @RequestBody RecheckRequest request) {
        return ApiResponse.success("复检结论已生效", service.recheck(id, request));
    }
}
