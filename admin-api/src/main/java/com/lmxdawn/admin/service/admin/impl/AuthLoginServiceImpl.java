package com.lmxdawn.admin.service.admin.impl;

import com.lmxdawn.admin.dto.admin.LoginUserInfoDTO;
import com.lmxdawn.admin.entity.AuthAdmin;
import com.lmxdawn.admin.entity.AuthPermission;
import com.lmxdawn.admin.entity.AuthPermissionRule;
import com.lmxdawn.admin.entity.AuthRoleAdmin;
import com.lmxdawn.admin.enums.ResultEnum;
import com.lmxdawn.admin.exception.JsonException;
import com.lmxdawn.admin.form.admin.LoginForm;
import com.lmxdawn.admin.form.admin.UpdatePasswordForm;
import com.lmxdawn.admin.service.admin.*;
import com.lmxdawn.admin.utils.JwtUtil;
import com.lmxdawn.admin.utils.PasswordUtil;
import com.lmxdawn.admin.utils.PublicFileUtil;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AuthLoginServiceImpl implements AuthLoginService {

    @Resource
    private AuthAdminService authAdminService;

    @Resource
    private AuthRoleAdminService authRoleAdminService;

    @Resource
    private AuthPermissionService authPermissionService;

    @Resource
    private AuthPermissionRuleService authPermissionRuleService;

    /**
     * 生成登录凭证
     * @return
     */
    @Override
    public Map<String, Object> loginToken(LoginForm loginForm) {
        AuthAdmin authAdmin = authAdminService.findByUserName(loginForm.getUserName());
        if (authAdmin == null) {
            throw new JsonException(ResultEnum.DATA_NOT, "用户名或密码错误");
        }

        if (!PasswordUtil.authAdminPwd(loginForm.getPwd()).equals(authAdmin.getPassword())) {
            throw new JsonException(ResultEnum.DATA_NOT, "用户名或密码错误");
        }

        Map<String, Object> claims = new HashMap<>();
        claims.put("admin_id", authAdmin.getId());
        String token = JwtUtil.createToken(claims, 86400L); // 一天后过期

        Map<String, Object> map = new HashMap<>();
        map.put("id", authAdmin.getId());
        map.put("token", token);
        return map;
    }

    /**
     * 根据 adminId 获取需要返回的登录信息
     * @param adminId
     * @return
     */
    @Override
    public LoginUserInfoDTO findByAdminId(Long adminId) {

        AuthAdmin authAdmin = authAdminService.findById(adminId);
        if (authAdmin == null) {
            throw new JsonException(ResultEnum.DATA_NOT);
        }
        authAdmin.setAvatar(PublicFileUtil.createUploadUrl(authAdmin.getAvatar()));

        // 获取权限列表
        List<String> authRules = new ArrayList<>();
        if (authAdmin.getUsername().equals("admin")) {
            authRules.add("admin");
        } else {
            // 获取角色ids
            List<AuthRoleAdmin> authRoleAdmins = authRoleAdminService.selectByAdminId(adminId);
            List<Long> roleIds = new ArrayList<>();
            if (authRoleAdmins != null && !authRoleAdmins.isEmpty()) {
                roleIds = authRoleAdmins.stream().map(AuthRoleAdmin::getRoleId).collect(Collectors.toList());
            }
            List<AuthPermission> authPermissions = new ArrayList<>();
            if (!roleIds.isEmpty()) {
                // 角色授权列表
                authPermissions = authPermissionService.selectByRoleIdIn(roleIds);
            }
            List<Long> permissionRuleIds = new ArrayList<>();
            if (!authPermissions.isEmpty()) {
                permissionRuleIds = authPermissions.stream().map(AuthPermission::getPermissionRuleId).collect(Collectors.toList());
            }
            List<AuthPermissionRule> authPermissionRules = new ArrayList<>();
            if (!permissionRuleIds.isEmpty()) {
                // 获取授权的规则
                authPermissionRules = authPermissionRuleService.selectByIdIn(permissionRuleIds);
            }
            if (!authPermissionRules.isEmpty()) {
                authRules = authPermissionRules.stream().map(AuthPermissionRule::getName).collect(Collectors.toList());
            }
        }

        LoginUserInfoDTO loginUserInfoDTO = new LoginUserInfoDTO();
        BeanUtils.copyProperties(authAdmin, loginUserInfoDTO);
        loginUserInfoDTO.setAuthRules(authRules);
        return loginUserInfoDTO;
    }

    @Override
    public boolean updatePassword(UpdatePasswordForm updatePasswordForm) {

        AuthAdmin authAdmin = authAdminService.findPwdById(updatePasswordForm.getAdminId());
        if (authAdmin == null) {
            throw new JsonException(ResultEnum.DATA_NOT);
        }
        // 旧密码不对
        if (!authAdmin.getPassword().equals(PasswordUtil.authAdminPwd(updatePasswordForm.getOldPassword()))) {
            throw new JsonException(ResultEnum.DATA_NOT, "旧密码匹配失败");
        }

        authAdmin.setPassword(updatePasswordForm.getNewPassword());

        return authAdminService.updateAuthAdmin(authAdmin);
    }
}
