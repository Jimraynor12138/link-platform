package com.aaf.link.core.controller;

import com.aaf.link.common.result.Result;
import com.aaf.link.core.dto.ChannelStatsVO;
import com.aaf.link.core.dto.DailyStatsVO;
import com.aaf.link.core.dto.StatsRespDTO;
import com.aaf.link.core.service.StatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 短链统计查询接口（V1.3）
 */
@Tag(name = "短链统计")
@RestController
@RequestMapping("/api/v1/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;

    @Operation(summary = "查询短链某日统计（date 不传则查当天）")
    @GetMapping("/{code}")
    public Result<StatsRespDTO> stats(@PathVariable String code,
                                     @RequestParam(required = false) String date) {
        return Result.success(statsService.queryStats(code, date));
    }

    @Operation(summary = "看板：按日汇总 PV/UV（from/to 为 yyyy-MM-dd，默认近 7 天）")
    @GetMapping("/overview")
    public Result<List<DailyStatsVO>> overview(@RequestParam(required = false) String from,
                                               @RequestParam(required = false) String to) {
        return Result.success(statsService.queryDailyStats(from, to));
    }

    @Operation(summary = "看板：按渠道汇总 PV/UV（from/to 为 yyyy-MM-dd，默认近 7 天）")
    @GetMapping("/channels")
    public Result<List<ChannelStatsVO>> channels(@RequestParam(required = false) String from,
                                                 @RequestParam(required = false) String to) {
        return Result.success(statsService.queryChannelStats(from, to));
    }
}
