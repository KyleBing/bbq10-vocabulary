package cn.kylebing.bbq10vocab;

public class ProgressStats {
    public int total;
    public int known;
    public int unknown;
    public int unseen;
    public int due;
    public int stashed;

    public ProgressStats(int total, int known, int unknown) {
        this.total = total;
        this.known = known;
        this.unknown = unknown;
        this.unseen = total - known - unknown;
        if (this.unseen < 0) {
            this.unseen = 0;
        }
    }

    /** Plain header: 「会 12  不会 3  暂存 1  到期 0」（两项之间两个空格） */
    public String reviewLine() {
        return "会 " + known
                + "  不会 " + unknown
                + "  暂存 " + stashed
                + "  到期 " + due;
    }
}
