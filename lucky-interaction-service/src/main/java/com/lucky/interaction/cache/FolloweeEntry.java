package com.lucky.interaction.cache;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class FolloweeEntry {

    private final Long followUserId;

    private final long followTimeMillis;
}
