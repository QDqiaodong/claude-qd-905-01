package com.glass.works.controller;

import com.glass.works.common.ApiResponse;
import com.glass.works.domain.GlassStock;
import com.glass.works.domain.StorageLocation;
import com.glass.works.dto.Requests.MoveRequest;
import com.glass.works.dto.Requests.WarehouseVO;
import com.glass.works.dto.StockRequest;
import com.glass.works.repository.StorageLocationRepository;
import com.glass.works.service.StockService;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 原片与库位 */
@RestController
@RequestMapping("/api/stock")
public class StockController {

    private final StockService service;
    private final StorageLocationRepository locationRepository;

    public StockController(StockService service, StorageLocationRepository locationRepository) {
        this.service = service;
        this.locationRepository = locationRepository;
    }

    @GetMapping("/list")
    public ApiResponse<List<GlassStock>> list() {
        return ApiResponse.success(service.list());
    }

    /** 顶部筛选玻璃条：按种类 / 厚度过滤 */
    @GetMapping("/filter")
    public ApiResponse<List<GlassStock>> filter(@RequestParam(required = false) String glassType,
                                                @RequestParam(required = false) Integer thickness) {
        return ApiResponse.success(service.filter(glassType, thickness));
    }

    /** 立体库位视图：区 → 架 → 层 → 原片批次块 */
    @GetMapping("/warehouse")
    public ApiResponse<WarehouseVO> warehouse() {
        return ApiResponse.success(service.warehouse());
    }

    @GetMapping("/locations")
    public ApiResponse<List<StorageLocation>> locations() {
        return ApiResponse.success(locationRepository.findAllByOrderByZoneCodeAscRackCodeAscLevelNoDesc());
    }

    @GetMapping("/{id}")
    public ApiResponse<GlassStock> get(@PathVariable Long id) {
        return ApiResponse.success(service.get(id));
    }

    @PostMapping
    public ApiResponse<GlassStock> create(@RequestBody StockRequest request) {
        return ApiResponse.success("原片批次已入库", service.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<GlassStock> update(@PathVariable Long id, @RequestBody StockRequest request) {
        return ApiResponse.success("原片批次已更新", service.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.success("原片批次已删除", null);
    }

    /** 上架到库位（承重不足要报错并给出剩余承重） */
    @PostMapping("/{id}/move")
    public ApiResponse<GlassStock> move(@PathVariable Long id, @RequestBody MoveRequest request) {
        return ApiResponse.success("上架成功", service.move(id, request));
    }

    /** 规格串反解演示：传 "8mm 2140×1650" 会把厚度 / 长 / 宽一起改写 */
    @PostMapping("/{id}/spec")
    public ApiResponse<GlassStock> applySpec(@PathVariable Long id, @RequestBody String specText) {
        String clean = specText == null ? "" : specText.replace("\"", "").trim();
        return ApiResponse.success("规格串已解析", service.applySpecText(id, clean));
    }
}
