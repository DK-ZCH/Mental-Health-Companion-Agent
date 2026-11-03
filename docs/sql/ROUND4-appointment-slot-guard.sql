-- ============================================================================
-- Phase 4 / Round 4 · 「一师一槽一有效预约」唯一约束
--
-- 背景（用户 2026-11-03 裁定 Q3-b）：
--   应用层查重负责业务语义；DB 唯一约束负责并发条件下的最终一致性兜底。
--   生成列让 status=3（老师拒绝）的预约【不再占用】槽位。
--
-- 时间模型前提（已取证）：
--   appointment_date (date) + time_slot (字典 1..8，每项固定 1 小时、互不重叠)
--   ⇒ 时间重叠  ⟺  同一 (appointment_date, time_slot)
--
-- 语义：
--   status = 3            -> guard = NULL  -> 不占槽（MySQL 唯一索引允许多个 NULL）
--   status = 1 / 2 / NULL -> guard = 1     -> 占槽（NULL 视为有效，故意保守）
--
-- 兼容性：执行前已按 (counselor_id, appointment_date, time_slot) 分组核对，
--         现有 3 条数据无重复 ⇒ 可直接执行。
--
-- ⚠️ 本项目无 Flyway/Liquibase：需手工执行，并在交付文档记录执行时间与结果。
-- ============================================================================

-- ---------- 正向 ----------
ALTER TABLE counseling_appointment
  ADD COLUMN active_slot_guard TINYINT
      GENERATED ALWAYS AS (CASE WHEN status = 3 THEN NULL ELSE 1 END) STORED,
  ADD UNIQUE KEY uk_counselor_slot
      (counselor_id, appointment_date, time_slot, active_slot_guard);

-- ---------- 回滚 ----------
-- ALTER TABLE counseling_appointment
--   DROP INDEX uk_counselor_slot,
--   DROP COLUMN active_slot_guard;
