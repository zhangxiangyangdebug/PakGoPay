package com.pakgopay.common.entity;

import lombok.Data;

import java.util.List;

@Data
public class MenuItem {
    private String menuId;
    private String menuName;
    private String nameEn;
    private Integer menuLevel;
    private String parentId;
    private String path;
    private String url;
    private String icon;
    private String roleMap;
    private boolean showItem;
    private String meta;
    private String component;
    private String redirect;
    private List<Children> children;
}
