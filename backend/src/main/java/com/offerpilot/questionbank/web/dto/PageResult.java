package com.offerpilot.questionbank.web.dto;

import java.util.List;

public record PageResult<T>(List<T> records, long total, long page, long size) {
}
