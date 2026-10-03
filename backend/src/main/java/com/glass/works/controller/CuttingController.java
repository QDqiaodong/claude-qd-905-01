package com.glass.works.controller;

import com.glass.works.common.ApiResponse;
import com.glass.works.domain.CuttingOrder;
import com.glass.works.dto.CuttingRequests.OrderRequest;
import com.glass.works.dto.CuttingRequests.PreviewRequest;
import com.glass.works.dto.CuttingRequests.PreviewResult;
import com.glass.works.dto.RemnantRankDTO;
import com.glass.works.service.CuttingService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 切割与优化 */
@RestController
@RequestMapping("/api/cutting")
public class CuttingController {

    private final CuttingService service;

    public CuttingController(CuttingService service) {
        this.service = service;
    }

    @GetMapping("/list")
    public ApiResponse<List<CuttingOrder>> list() {
        return ApiResponse.success(service.list());
    }

    /** 切割单 + 出片规格清单 */
    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long id) {
        return ApiResponse.success(service.detail(id));
    }

    /** 试算：不落库，即时返回利用率、余料与布局矩形 */
    @PostMapping("/preview")
    public ApiResponse<PreviewResult> preview(@RequestBody PreviewRequest request) {
        return ApiResponse.success(service.preview(request));
    }

    /** 生成切割单：出片面积超出原片面积会报错并给出超出面积 */
    @PostMapping
    public ApiResponse<CuttingOrder> create(@RequestBody OrderRequest request) {
        return ApiResponse.success("切割单已生成", service.create(request));
    }

    @PostMapping("/{id}/status")
    public ApiResponse<CuttingOrder> updateStatus(@PathVariable Long id, @RequestParam String status) {
        return ApiResponse.success("切割单状态已更新", service.updateStatus(id, status));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.success("切割单已删除", null);
    }

    /** 原片余料排行（手写 JPQL） */
    @GetMapping("/remnant-rank")
    public ApiResponse<List<RemnantRankDTO>> remnantRank() {
        return ApiResponse.success(service.remnantRank());
    }
}
