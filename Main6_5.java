import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Bài 6.5 – Ví dụ Model/View/Controller của Hình 6-16 và 6-17.
 *
 * Model      : File (tên file "9DesignPatterns2.ppt")
 * View       : InfoView (cửa sổ Info), FolderView (cửa sổ thư mục CBSE)
 * Controller : Controller (nhận tên mới do người dùng nhập)
 *
 * Luồng thông điệp đúng theo sơ đồ tuần tự của bài 6.5a:
 *   1a/1b subscribeToFileEvents(f) -> 2 enterNewFileName(file,newName) -> 3 setName(newName)
 *   -> 4a/4b notifySubscribedViews(file) -> 5a/5b getName()
 *
 * Phần cuối minh họa thêm ý 6.5b (access control): bọc Model bằng một Proxy kiểm tra quyền.
 *
 * Biên dịch & chạy:  javac -d out src/Main.java && java -cp out Main
 */

/* ===================== Model ===================== */

/** Giao diện mà View theo dõi (mẫu Observer). */
interface FileListener {
    void notifySubscribedViews(FileModel file);
}

/** Giao diện Model, để có thể bọc thêm Proxy kiểm tra quyền (ý 6.5b). */
interface FileModel {
    String getName();
    void setName(String newName);
    void subscribeToFileEvents(FileListener view);
}

class File implements FileModel {
    private String name;
    private final List<FileListener> subscribers = new ArrayList<>();

    File(String name) { this.name = name; }

    @Override
    public String getName() { return name; }

    @Override
    public void subscribeToFileEvents(FileListener view) {
        subscribers.add(view);
    }

    @Override
    public void setName(String newName) {
        this.name = newName;
        // 4a, 4b: Model chỉ biết các View qua giao diện FileListener
        for (FileListener v : subscribers) {
            Trace.msg(":File", v.toString(), "notifySubscribedViews(file)");
            v.notifySubscribedViews(this);
        }
    }
}

/* ===================== View ===================== */

class InfoView implements FileListener {
    private String shownName = "";

    InfoView(FileModel file) {
        Trace.msg(":InfoView", ":File", "subscribeToFileEvents(f)");       // 1a
        file.subscribeToFileEvents(this);
        shownName = file.getName();
    }

    @Override
    public void notifySubscribedViews(FileModel file) {
        Trace.msg(":InfoView", ":File", "getName()");                      // 5a
        shownName = file.getName();
        render();
    }

    void render() { System.out.println("    [Info]  Name: " + shownName); }

    @Override
    public String toString() { return ":InfoView"; }
}

class FolderView implements FileListener {
    private String shownName = "";

    FolderView(FileModel file) {
        Trace.msg(":FolderView", ":File", "subscribeToFileEvents(f)");     // 1b
        file.subscribeToFileEvents(this);
        shownName = file.getName();
    }

    @Override
    public void notifySubscribedViews(FileModel file) {
        Trace.msg(":FolderView", ":File", "getName()");                    // 5b
        shownName = file.getName();
        render();
    }

    void render() { System.out.println("    [CBSE]  " + shownName); }

    @Override
    public String toString() { return ":FolderView"; }
}

/* ===================== Controller ===================== */

class Controller {
    void enterNewFileName(FileModel file, String newName) {                // 2
        Trace.msg(":Controller", ":File", "setName(newName)");             // 3
        file.setName(newName);
    }
}

/* ===================== Ý 6.5b: kiểm soát truy cập bằng Proxy ===================== */

/** Proxy đứng trước Model: chỉ người có quyền "rename" mới được đổi tên. */
class ProtectedFile implements FileModel {
    private final FileModel real;
    private final String currentUser;
    private final Set<String> canRename;

    ProtectedFile(FileModel real, String currentUser, Set<String> canRename) {
        this.real = real;
        this.currentUser = currentUser;
        this.canRename = canRename;
    }

    @Override public String getName() { return real.getName(); }
    @Override public void subscribeToFileEvents(FileListener v) { real.subscribeToFileEvents(v); }

    @Override
    public void setName(String newName) {
        if (!canRename.contains(currentUser)) {
            throw new SecurityException(currentUser + " không có quyền đổi tên file");
        }
        real.setName(newName);
    }
}

/* ===================== Tiện ích in vết thông điệp ===================== */

final class Trace {
    private static int step = 0;
    private Trace() {}

    static void msg(String from, String to, String message) {
        System.out.printf("%2d. %-12s -> %-12s : %s%n", ++step, from, to, message);
    }

    static void reset() { step = 0; }
}

public class Main {
    public static void main(String[] args) {
        System.out.println("=== 6.5a: Đổi tên file, cả hai View được cập nhật ===");
        File file = new File("9DesignPatterns2.ppt");
        new InfoView(file);
        new FolderView(file);
        Controller controller = new Controller();

        Trace.msg("User", ":Controller", "enterNewFileName(file, newName)");
        controller.enterNewFileName(file, "9DesignPatterns.ppt");

        System.out.println();
        System.out.println("=== 6.5b: Access control qua Proxy đặt trước Model ===");
        Trace.reset();
        File shared = new File("bao-cao.docx");
        new InfoView(shared);
        FileModel asGuest = new ProtectedFile(shared, "guest", Set.of("tri"));
        FileModel asTri = new ProtectedFile(shared, "tri", Set.of("tri"));
        try {
            controller.enterNewFileName(asGuest, "bi-sua.docx");
        } catch (SecurityException e) {
            System.out.println("    Từ chối: " + e.getMessage());
        }
        controller.enterNewFileName(asTri, "bao-cao-v2.docx");
    }
}
