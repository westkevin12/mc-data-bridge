import org.bukkit.NamespacedKey;
import java.io.*;

public class TestSerial {
    public static void main(String[] args) throws Exception {
        NamespacedKey key = NamespacedKey.minecraft("test");
        ByteArrayOutputStream bao = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(bao);
        try {
            oos.writeObject(key);
            System.out.println("NamespacedKey is Serializable");
        } catch (NotSerializableException e) {
            System.out.println("NamespacedKey is NOT Serializable");
        }
    }
}
