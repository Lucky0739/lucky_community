package com.lucky.interaction.support.entity;

import lombok.Data;

@Data
public class BarBrief {

    private Long id;
    private String name;
    private String avatar;
    private Integer followCount;

    private Integer postCount;
}
