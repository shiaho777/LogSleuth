package io.github.shiaho777.logsleuth.app.data.db

import io.github.shiaho777.logsleuth.app.core.filter.LogFilter
import io.github.shiaho777.logsleuth.app.core.filter.decodeLevels
import io.github.shiaho777.logsleuth.app.core.filter.encodeLevels
import io.github.shiaho777.logsleuth.app.core.logcat.LogLevel

fun FilterEntity.toLogFilter(): LogFilter = LogFilter(
    id = id,
    name = name,
    minLevel = runCatching { LogLevel.valueOf(minLevel) }.getOrDefault(LogLevel.V),
    allowedLevels = decodeLevels(allowedLevels),
    query = query,
    excludeQuery = excludeQuery,
    tagQuery = tagQuery,
    tagExact = tagExact,
    useRegex = useRegex,
    packageName = packageName,
    enabled = enabled,
    including = including,
    pid = pid,
    tid = tid,
    uid = uid,
)

fun LogFilter.toEntity(): FilterEntity = FilterEntity(
    id = id,
    name = name,
    minLevel = minLevel.name,
    allowedLevels = allowedLevels.encodeLevels(),
    query = query,
    excludeQuery = excludeQuery,
    tagQuery = tagQuery,
    tagExact = tagExact,
    useRegex = useRegex,
    packageName = packageName,
    enabled = enabled,
    including = including,
    pid = pid,
    tid = tid,
    uid = uid,
)
