package com.aaf.link.auth.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新建租户请求（P6 平台侧开通）。
 *
 * <p>开通租户时同时创建该租户的初始管理员账号，避免"建了租户没人能登录"。
 */
@Data
public class TenantCreateReqDTO {

    @NotBlank(message = "租户名称不能为空")
    @Size(max = 64, message = "租户名称长度不能超过 64")
    private String name;

    /** 每日创建短链配额，空或 <=0 则用默认值 100000 */
    private Long quotaPerDay;

    @NotBlank(message = "管理员用户名不能为空")
    @Size(max = 64, message = "管理员用户名长度不能超过 64")
    private String adminUsername;

    @NotBlank(message = "管理员密码不能为空")
    @Size(min = 6, max = 64, message = "密码长度需在 6~64 之间")
    private String adminPassword;

    @Size(max = 64, message = "昵称长度不能超过 64")
    private String adminNickname;
}
