import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Bài 6.3 – Phân rã hệ thống con để chuẩn bị chuyển nền tảng.
 *
 * Kiến trúc phân lớp đóng:
 *   UserInterface -> ApplicationLogic -> Storage (interface) -> [UnixStorage | WindowsStorage | ...]
 *
 * Chỉ các lớp hiện thực Storage biết về hệ điều hành. Chuyển sang nền tảng mới
 * = viết thêm một lớp hiện thực Storage, không sửa ApplicationLogic hay UserInterface.
 *
 * Biên dịch & chạy:  javac -d out src/Main.java && java -cp out Main
 */

/* ===================== Hệ thống con Storage ===================== */

/** Ngoại lệ chung của hệ thống con Storage (che lỗi riêng của từng nền tảng). */
class StorageException extends Exception {
    StorageException(String msg, Throwable cause) { super(msg, cause); }
}

/** Giao diện duy nhất mà các hệ thống con khác được phép dùng. Đường dẫn luôn dùng dạng logic "a/b/c". */
interface Storage {
    String read(String logicalPath) throws StorageException;
    void write(String logicalPath, String data) throws StorageException;
    List<String> list(String logicalDir) throws StorageException;
    boolean exists(String logicalPath);
}

/** Phần dùng chung cho mọi nền tảng: ánh xạ đường dẫn logic sang đường dẫn vật lý. */
abstract class AbstractStorage implements Storage {
    protected final Path root;

    AbstractStorage(String rootDir) { this.root = Paths.get(rootDir); }

    /** Mỗi nền tảng tự quy định cách đổi đường dẫn logic thành đường dẫn thật. */
    protected abstract Path toPhysical(String logicalPath);

    @Override
    public String read(String logicalPath) throws StorageException {
        try {
            return Files.readString(toPhysical(logicalPath), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new StorageException("Không đọc được " + logicalPath, e);
        }
    }

    @Override
    public void write(String logicalPath, String data) throws StorageException {
        try {
            Path p = toPhysical(logicalPath);
            if (p.getParent() != null) Files.createDirectories(p.getParent());
            Files.writeString(p, data, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new StorageException("Không ghi được " + logicalPath, e);
        }
    }

    @Override
    public List<String> list(String logicalDir) throws StorageException {
        List<String> names = new ArrayList<>();
        try (Stream<Path> s = Files.list(toPhysical(logicalDir))) {
            s.forEach(p -> names.add(p.getFileName().toString()));
        } catch (IOException e) {
            throw new StorageException("Không liệt kê được " + logicalDir, e);
        }
        names.sort(String::compareTo);
        return names;
    }

    @Override
    public boolean exists(String logicalPath) { return Files.exists(toPhysical(logicalPath)); }
}

/** Hiện thực cho Unix: dấu phân cách '/', phân biệt hoa thường. */
class UnixStorage extends AbstractStorage {
    UnixStorage(String rootDir) { super(rootDir); }

    @Override
    protected Path toPhysical(String logicalPath) {
        return root.resolve(logicalPath);
    }
}

/** Hiện thực cho Windows: dấu phân cách '\', tên file không phân biệt hoa thường. */
class WindowsStorage extends AbstractStorage {
    WindowsStorage(String rootDir) { super(rootDir); }

    @Override
    protected Path toPhysical(String logicalPath) {
        String winPath = logicalPath.replace('/', '\\').toLowerCase();
        // Trên JVM chạy Windows, Paths.get hiểu dấu '\'; trên Unix ta đổi lại để demo vẫn chạy được.
        String sep = FileSystems.getDefault().getSeparator();
        return root.resolve(winPath.replace("\\", sep));
    }
}

/** Điểm duy nhất chọn hiện thực theo hệ điều hành. */
final class StorageFactory {
    private StorageFactory() {}

    static Storage create(String rootDir) {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) return new WindowsStorage(rootDir);
        return new UnixStorage(rootDir); // Linux, macOS và các hệ Unix khác
    }
}

/* ===================== Hệ thống con ApplicationLogic ===================== */

/** Nghiệp vụ: lưu và đọc ghi chú. Không có dòng nào phụ thuộc hệ điều hành. */
class NoteService {
    private final Storage storage;

    NoteService(Storage storage) { this.storage = storage; }

    void saveNote(String title, String content) throws StorageException {
        storage.write("notes/" + title + ".txt", content);
    }

    String openNote(String title) throws StorageException {
        return storage.read("notes/" + title + ".txt");
    }

    List<String> allNotes() throws StorageException {
        return storage.exists("notes") ? storage.list("notes") : List.of();
    }
}

/* ===================== Hệ thống con UserInterface ===================== */

class ConsoleUI {
    private final NoteService service;

    ConsoleUI(NoteService service) { this.service = service; }

    void run() {
        try {
            service.saveNote("bai-6-3", "Storage là lớp duy nhất phụ thuộc vào hệ điều hành.");
            service.saveNote("ghi-chu", "Thêm nền tảng mới = thêm một lớp hiện thực Storage.");
            System.out.println("Danh sách ghi chú: " + service.allNotes());
            System.out.println("Nội dung bai-6-3: " + service.openNote("bai-6-3"));
        } catch (StorageException e) {
            System.out.println("Lỗi: " + e.getMessage());
        }
    }
}

public class Main {
    public static void main(String[] args) throws IOException {
        Path dataDir = Files.createTempDirectory("bai63-data");
        Storage storage = StorageFactory.create(dataDir.toString());
        System.out.println("Hệ điều hành: " + System.getProperty("os.name")
                + " -> dùng " + storage.getClass().getSimpleName());
        new ConsoleUI(new NoteService(storage)).run();
    }
}
