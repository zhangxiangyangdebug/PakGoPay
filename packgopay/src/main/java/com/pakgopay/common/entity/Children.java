package com.pakgopay.common.entity;

import lombok.Data;

@Data
public class Children {
    private String menuId;
    private String menuName;
    private Integer menuLevel;
    private String parentId;
    private String path;
    private String url;
    private String icon;
    private String roleMap;
}
