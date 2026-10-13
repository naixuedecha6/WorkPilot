package com.workpilot.tools;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * 通用 MapLike 适配器:把任意 Map<String,Object> 包成工具可以方便访问的接口。
 */
public class Args {
    private final Map<String, Object> raw;

    private Args(Map<String, Object> raw) {
        this.raw = raw == null ? Map.of() : raw;
    }

    public static Args of(Map<String, Object> raw) {
        return new Args(raw);
    }

    public static Args empty() {
        return new Args(Map.of());
    }

    public Tool.MapLike asMapLike() {
        return new Tool.MapLike() {
            @Override public String getString(String key) {
                Object v = raw.get(key);
                return v == null ? null : v.toString();
            }

            @Override public Integer getInt(String key, Integer defaultValue) {
                Object v = raw.get(key);
                if (v == null) return defaultValue;
                if (v instanceof Number n) return n.intValue();
                try { return Integer.parseInt(v.toString()); } catch (NumberFormatException e) { return defaultValue; }
            }

            @Override public Long getLong(String key, Long defaultValue) {
                Object v = raw.get(key);
                if (v == null) return defaultValue;
                if (v instanceof Number n) return n.longValue();
                try { return Long.parseLong(v.toString()); } catch (NumberFormatException e) { return defaultValue; }
            }

            @Override public Instant getInstant(String key) {
                Object v = raw.get(key);
                if (v == null) return null;
                try { return Instant.parse(v.toString()); } catch (DateTimeParseException e) { return null; }
            }

            @Override public <T extends Enum<T>> T getEnum(String key, Class<T> type, T defaultValue) {
                Object v = raw.get(key);
                if (v == null) return defaultValue;
                try { return Enum.valueOf(type, v.toString()); } catch (IllegalArgumentException e) { return defaultValue; }
            }

            @Override public List<Tool.MapLike> getList(String key) {
                Object v = raw.get(key);
                if (!(v instanceof List<?> list)) return List.of();
                List<Tool.MapLike> out = new ArrayList<>();
                for (Object item : list) {
                    if (item instanceof Map<?, ?> m) {
                        Map<String, Object> casted = new LinkedHashMap<>();
                        m.forEach((k, val) -> casted.put(String.valueOf(k), val));
                        out.add(Args.of(casted).asMapLike());
                    }
                }
                return out;
            }

            @Override public boolean contains(String key) {
                return raw.containsKey(key);
            }
        };
    }
}
