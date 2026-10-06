package org.encinet.mik.module.governance;

public final class GovernanceException extends Exception {
    public enum Code {
        PLAYER_UNKNOWN("该玩家尚无治理记录"),
        PAUSE_END_INVALID("晋升暂停期限必须晚于当前时间"),
        PAUSE_ALREADY_ACTIVE("该玩家已有生效中的晋升暂停"),
        PAUSE_ALREADY_MEMBER("该玩家已是成员，无需暂停晋升"),
        VOTERS_TOO_FEW("至少需要两名活跃成员才能发起投票"),
        VOTE_SUBJECT_UNKNOWN("投票对象尚无治理记录"),
        VOTE_ALREADY_OPEN("该玩家已有进行中的同类投票"),
        VOTE_COOLDOWN("未达到法定参与人数后的三天重试间隔尚未结束"),
        VOTE_UNKNOWN("投票不存在"),
        VOTE_CLOSED("投票已经截止或终止"),
        NOT_VOTER("你不在本次冻结选民名单中"),
        VOTE_TERMINATION_CLOSED("投票截止时间已过，不能再终止或撤回"),
        CANDIDATE_INELIGIBLE("管理候选人必须是活跃成员"),
        REMOVAL_REQUIRES_PETITION("罢免投票必须由达到人数的共同发起申请启动"),
        BAN_SELF("不能发起封禁自己的投票"),
        BAN_PROPOSER_INELIGIBLE("只有活跃成员可以发起封禁投票"),
        BAN_ALREADY_ACTIVE("该玩家已有生效中的封禁"),
        TERMINATION_REASON_INVALID("终止原因不适用于该类投票"),
        REMOVAL_SELF("被罢免者不能共同发起对自己的罢免"),
        REMOVAL_SPONSOR_INELIGIBLE("只有活跃成员可以共同发起罢免"),
        REMOVAL_SPONSORS_TOO_FEW("除被罢免者外至少需要两名活跃成员"),
        REMOVAL_VOTE_OPEN("该管理已有进行中的罢免投票"),
        NOMINATION_NOT_OPEN("你没有进行中的管理报名");

        private final String defaultMessage;

        Code(String defaultMessage) {
            this.defaultMessage = defaultMessage;
        }

        public String defaultMessage() {
            return defaultMessage;
        }
    }

    private final Code code;

    public GovernanceException(Code code) {
        super(code.defaultMessage());
        this.code = code;
    }

    public GovernanceException(String message) {
        super(message);
        this.code = null;
    }

    public GovernanceException(String message, Throwable cause) {
        super(message, cause);
        this.code = null;
    }

    public Code code() {
        return code;
    }
}
