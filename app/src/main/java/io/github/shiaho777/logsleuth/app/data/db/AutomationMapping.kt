package io.github.shiaho777.logsleuth.app.data.db

import io.github.shiaho777.logsleuth.app.core.automation.AutomationRule

fun AutomationRuleEntity.toRule(): AutomationRule = AutomationRule(
    id = id,
    name = name,
    enabled = enabled,
    packageName = packageName,
    query = query,
    useRegex = useRegex,
    tagQuery = tagQuery,
    targetPackage = targetPackage,
    action = action,
    cooldownSeconds = cooldownSeconds,
    createdAt = createdAt,
)

fun AutomationRule.toEntity(): AutomationRuleEntity = AutomationRuleEntity(
    id = id,
    name = name,
    enabled = enabled,
    packageName = packageName,
    query = query,
    useRegex = useRegex,
    tagQuery = tagQuery,
    targetPackage = targetPackage,
    action = action,
    cooldownSeconds = cooldownSeconds,
    createdAt = createdAt,
)