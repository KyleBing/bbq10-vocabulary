package cn.kylebing.bbq10vocab.db;

public final class BuiltInBooks {
    public static final Item[] ALL = new Item[]{
            new Item("四级", "cet4.db", "books/cet4.db"),
            new Item("六级", "cet6.db", "books/cet6.db"),
            new Item("高中", "gaozhong.db", "books/gaozhong.db"),
            new Item("初中", "chuzhong.db", "books/chuzhong.db"),
            new Item("考研", "kaoyan.db", "books/kaoyan.db"),
    };

    private BuiltInBooks() {
    }

    public static class Item {
        public final String title;
        public final String dbFileName;
        public final String assetPath;

        public Item(String title, String dbFileName, String assetPath) {
            this.title = title;
            this.dbFileName = dbFileName;
            this.assetPath = assetPath;
        }
    }
}
