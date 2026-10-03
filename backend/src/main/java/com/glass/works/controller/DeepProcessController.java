package com.glass.works.controller;

import com.glass.works.common.ApiResponse;
import com.glass.works.domain.DeepProcessRecord;
import com.glass.works.dto.DeepProcessRequest;
import com.glass.works.dto.ProcessStatDTO;
import com.glass.works.service.DeepProcessService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 深加工工序：镀膜 / 中空 / 夹胶 */
@RestController
@RequestMapping("/api/deep")
public class DeepProcessController {

    private final DeepProcessService service;

    public DeepProcessController(DeepProcessService service) {
        this.service = service;
    }

    @GetMapping("/list")
    public ApiResponse<List<DeepProcessRecord>> list() {
        return ApiResponse.success(service.list());
    }

    @GetMapping("/list/{processType}")
    public ApiResponse<List<DeepProcessRecord>> listByType(@PathVariable String processType) {
        return ApiResponse.success(service.listByType(processType));
    }

    /**
     * 投产登记：中空露点高于 -40℃ 判不合格；
     * 夹胶 PVB 与玻璃厚度不匹配不允许投产。
     */
    @PostMapping
    public ApiResponse<DeepProcessRecord> create(@RequestBody DeepProcessRequest request) {
        return ApiResponse.success("工序记录已保存", service.create(request));
    }

    @GetMapping("/stat")
    public ApiResponse<List<ProcessStatDTO>> stat() {
        return ApiResponse.success(service.stat());
    }
}
