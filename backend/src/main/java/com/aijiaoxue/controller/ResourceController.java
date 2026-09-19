package com.aijiaoxue.controller;

import com.aijiaoxue.audit.AuditLog;
import com.aijiaoxue.common.ApiResponse;
import com.aijiaoxue.common.PageResult;
import com.aijiaoxue.entity.TeachingResource;
import com.aijiaoxue.entity.view.ResourceView;
import com.aijiaoxue.security.CurrentUserHolder;
import com.aijiaoxue.security.PermissionCodes;
import com.aijiaoxue.service.ResourceService;
import com.aijiaoxue.util.CsvUtils;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 教学资源接口（史诗二，US-03）。
 *
 * <p>契约见 docs/API契约.md 第 5 节。教师角色调用列表接口时自动收窄为本人相关资源。
 */
@RestController
@RequestMapping("/api/resources")
public class ResourceController {

    private final ResourceService resourceService;

    public ResourceController(ResourceService resourceService) {
        this.resourceService = resourceService;
    }

    /** 资源列表分页 */
    @GetMapping
    @PreAuthorize("hasAuthority('" + PermissionCodes.RESOURCE_READ + "')")
    public ApiResponse<PageResult<ResourceView>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long offeringId) {
        return ApiResponse.ok(resourceService.page(page, size, keyword, resourceType, status, offeringId,
                CurrentUserHolder.require()));
    }

    /** 备课查看：按「我的课程 → 可用资源」分组 */
    @GetMapping("/prep")
    @PreAuthorize("hasAuthority('" + PermissionCodes.RESOURCE_PREP + "')")
    public ApiResponse<Map<String, Object>> prep() {
        return ApiResponse.ok(resourceService.prepView(CurrentUserHolder.require()));
    }

    /** 资源详情 */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionCodes.RESOURCE_READ + "')")
    public ApiResponse<ResourceView> detail(@PathVariable Long id) {
        return ApiResponse.ok(resourceService.detail(id));
    }

    /** 某课程已挂接的资源 */
    @GetMapping("/by-course/{courseId}")
    @PreAuthorize("hasAuthority('" + PermissionCodes.RESOURCE_READ + "')")
    public ApiResponse<List<ResourceView>> byCourse(@PathVariable Long courseId) {
        return ApiResponse.ok(resourceService.byCourse(courseId));
    }

    /** 新建资源 */
    @PostMapping
    @PreAuthorize("hasAuthority('" + PermissionCodes.RESOURCE_WRITE + "')")
    @AuditLog(action = "新增", targetType = "RESOURCE")
    public ApiResponse<TeachingResource> create(@RequestBody TeachingResource resource) {
        return ApiResponse.ok(resourceService.create(resource, CurrentUserHolder.require()));
    }

    /** 修改资源 */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionCodes.RESOURCE_WRITE + "')")
    @AuditLog(action = "修改", targetType = "RESOURCE", idParamIndex = 0)
    public ApiResponse<TeachingResource> update(@PathVariable Long id,
                                                @RequestBody TeachingResource resource) {
        return ApiResponse.ok(resourceService.update(id, resource));
    }

    /** 删除资源 */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionCodes.RESOURCE_WRITE + "')")
    @AuditLog(action = "删除", targetType = "RESOURCE", idParamIndex = 0)
    public ApiResponse<Void> delete(@PathVariable Long id) {
        resourceService.delete(id);
        return ApiResponse.ok();
    }

    /** 导出资源 CSV */
    @GetMapping("/export")
    @PreAuthorize("hasAuthority('" + PermissionCodes.RESOURCE_READ + "')")
    @AuditLog(action = "导出", targetType = "RESOURCE")
    public void export(@RequestParam(required = false) String keyword,
                       @RequestParam(required = false) String resourceType,
                       @RequestParam(required = false) String status,
                       @RequestParam(required = false) Long offeringId,
                       HttpServletResponse response) throws IOException {
        List<ResourceView> list = resourceService.listForExport(keyword, resourceType, status, offeringId,
                CurrentUserHolder.require());
        List<List<String>> rows = new ArrayList<>();
        for (ResourceView r : list) {
            List<String> row = new ArrayList<>();
            row.add(r.getResourceName());
            row.add(r.getResourceType());
            row.add(r.getCourseCode());
            row.add(r.getCourseName());
            row.add(r.getOfferingCode());
            row.add(r.getSemester());
            row.add(r.getTeacherName());
            row.add(r.getFileUrl());
            row.add(r.getFileSize() == null ? "" : String.valueOf(r.getFileSize()));
            row.add(r.getStatus());
            row.add(r.getDescription());
            rows.add(row);
        }
        CsvUtils.write(response, "教学资源导出",
                List.of("资源名称", "资源类型", "课程编号", "课程名称", "教学班编号", "学期",
                        "归属教师", "资源地址", "文件大小", "状态", "资源说明"), rows);
    }

    /** 资源类型候选值 */
    @GetMapping("/types")
    @PreAuthorize("hasAuthority('" + PermissionCodes.RESOURCE_READ + "')")
    public ApiResponse<List<String>> types() {
        return ApiResponse.ok(resourceService.resourceTypes());
    }
}
