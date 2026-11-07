package cn.kylebing.bbq10vocab.db;

public final class BuiltInBooks {
    /** Easy → hard. Default book is {@link #CET4}, not necessarily ALL[0]. */
    public static final Item CET4 = new Item("四级", "cet4.db", "books/cet4.db", 4544);
    public static final Item[] ALL = new Item[]{
            new Item("初中", "chuzhong.db", "books/chuzhong.db", 1991),
            new Item("高中", "gaozhong.db", "books/gaozhong.db", 3753),
            CET4,
            new Item("六级", "cet6.db", "books/cet6.db", 3992),
            new Item("考研", "kaoyan.db", "books/kaoyan.db", 5057),
    };

    private BuiltInBooks() {
    }

    public static class Item {
        public final String title;
        public final String dbFileName;
        public final String assetPath;
        /** Unique headword count packaged in assets (after dedupe). */
        public final int wordCount;

        public Item(String title, String dbFileName, String assetPath, int wordCount) {
            this.title = title;
            this.dbFileName = dbFileName;
            this.assetPath = assetPath;
            this.wordCount = wordCount;
        }
    }
}
