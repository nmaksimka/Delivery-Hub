package com.deliveryhub.search.api;

import java.util.List;

public record SearchPageDto(List<SearchHitDto> items, long total, int page, int size) {
}
