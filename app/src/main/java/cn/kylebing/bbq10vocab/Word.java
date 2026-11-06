package cn.kylebing.bbq10vocab;

public class Word {
    public long id;
    public String word;
    public String us;
    public String uk;
    public String trans;
    public String exampleEn;
    public String exampleCn;
    public int status;
    /** 1 = 暂存/不太会，需要之后加强练习 */
    public int flag;

    public boolean isStashed() {
        return flag == 1;
    }

    public String phonetic() {
        if (us != null && us.length() > 0) {
            return "/" + us + "/";
        }
        if (uk != null && uk.length() > 0) {
            return "/" + uk + "/";
        }
        return "";
    }
}
