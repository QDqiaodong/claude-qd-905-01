package com.glass.works.service;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 启动回填：历史判返修的检验记录按「同工序 + 批次/订单标识桥接」归并成返修批次。
 * 幂等 —— 返修批次表非空（已回填过或已有新闭环单）即跳过。
 */
@Component
public class ReworkBackfillRunner implements ApplicationRunner {

    private final ReworkService reworkService;

    public ReworkBackfillRunner(ReworkService reworkService) {
        this.reworkService = reworkService;
    }

    @Override
    public void run(ApplicationArguments args) {
        reworkService.backfillLegacyIfEmpty();
    }
}
