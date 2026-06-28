# 04 — Design Patterns: Structural

---

## Decorator

**Intent**: Add responsibilities to objects dynamically without modifying their class.

**When to use**: Layered behavior (logging, caching, auth, compression). Subclassing would cause explosion of classes.

```java
public interface DataSource {
    void write(String data);
    String read();
}

public class FileDataSource implements DataSource {
    private final String filename;
    public FileDataSource(String filename) { this.filename = filename; }
    public void write(String data) { /* write to file */ }
    public String read() { return /* read from file */ "data"; }
}

// Base decorator
public abstract class DataSourceDecorator implements DataSource {
    protected final DataSource wrapped;
    public DataSourceDecorator(DataSource source) { this.wrapped = source; }
    public void write(String data) { wrapped.write(data); }
    public String read() { return wrapped.read(); }
}

public class EncryptionDecorator extends DataSourceDecorator {
    public EncryptionDecorator(DataSource source) { super(source); }
    public void write(String data) { super.write(encrypt(data)); }
    public String read() { return decrypt(super.read()); }
    private String encrypt(String data) { return Base64.getEncoder().encodeToString(data.getBytes()); }
    private String decrypt(String data) { return new String(Base64.getDecoder().decode(data)); }
}

public class CompressionDecorator extends DataSourceDecorator {
    public CompressionDecorator(DataSource source) { super(source); }
    public void write(String data) { super.write(compress(data)); }
    public String read() { return decompress(super.read()); }
    private String compress(String data) { return data; /* GZIP logic */ }
    private String decompress(String data) { return data; }
}

// Composing decorators — order matters!
DataSource source = new CompressionDecorator(
                        new EncryptionDecorator(
                            new FileDataSource("data.txt")));
// Write: compress → encrypt → file
// Read: file → decrypt → decompress
```

### Decorator Traps

**Q: Decorator vs Inheritance — why prefer Decorator?**
A: Inheritance is static (decided at compile time). Decorator is dynamic (add/remove at runtime). With inheritance you'd need `EncryptedFile`, `CompressedFile`, `EncryptedCompressedFile` — combinatorial explosion.

**Q: What's wrong with stacking too many decorators?**
A: Hard to debug. `toString()` on a 5-layer stack is confusing. Keep chains short and document order.

**Q: Decorator vs Proxy — what's the difference?**
A: Decorator adds behavior. Proxy controls access (same interface, but adds auth/caching/lazy-loading). Structurally identical — intent differs.

---

## Proxy

**Intent**: Provide a surrogate/placeholder for another object to control access.

**Types**:
- **Virtual Proxy**: Lazy initialization (expensive object created only when needed)
- **Protection Proxy**: Access control
- **Remote Proxy**: Represents object in different address space (RMI)
- **Caching Proxy**: Cache results

```java
public interface ImageLoader {
    void display();
}

public class RealImage implements ImageLoader {
    private final String filename;
    private byte[] data;
    
    public RealImage(String filename) {
        this.filename = filename;
        loadFromDisk(); // expensive!
    }
    
    private void loadFromDisk() {
        System.out.println("Loading " + filename + " from disk...");
        this.data = new byte[1024 * 1024]; // 1MB image
    }
    
    public void display() { System.out.println("Displaying " + filename); }
}

// Virtual Proxy — defers expensive loading
public class ImageProxy implements ImageLoader {
    private final String filename;
    private RealImage realImage; // null until first display()
    
    public ImageProxy(String filename) { this.filename = filename; }
    
    public void display() {
        if (realImage == null) realImage = new RealImage(filename); // lazy init
        realImage.display();
    }
}

// Protection Proxy — adds auth
public class SecureImageProxy implements ImageLoader {
    private final ImageLoader target;
    private final User currentUser;
    
    public SecureImageProxy(ImageLoader target, User currentUser) {
        this.target = target;
        this.currentUser = currentUser;
    }
    
    public void display() {
        if (!currentUser.hasPermission("VIEW_IMAGE")) {
            throw new SecurityException("Access denied");
        }
        target.display();
    }
}
```

### Proxy Traps

**Q: How does Java's dynamic proxy work?**
```java
ImageLoader proxy = (ImageLoader) Proxy.newProxyInstance(
    ImageLoader.class.getClassLoader(),
    new Class[]{ImageLoader.class},
    (proxyObj, method, args) -> {
        System.out.println("Before: " + method.getName());
        Object result = method.invoke(realImage, args);
        System.out.println("After: " + method.getName());
        return result;
    });
```
A: `java.lang.reflect.Proxy` creates a proxy at runtime without a concrete proxy class. Spring AOP uses this for `@Transactional`, `@Cacheable` etc.

**Q: What's the difference between Proxy and Facade?**
A: Proxy has the same interface as the target. Facade simplifies a complex subsystem with a new, simpler interface.

---

## Facade

**Intent**: Provide a simplified interface to a complex subsystem.

```java
// Complex subsystem
public class VideoDecoder { public RawVideo decode(String filename) { ... } }
public class AudioMixer { public Audio mix(RawVideo video) { ... } }
public class BitrateConverter { public EncodedVideo convert(RawVideo video, int bitrate) { ... } }
public class CodecFactory { public Codec getCodec(String format) { ... } }

// Facade — one simple call hides all complexity
public class VideoConversionFacade {
    private final VideoDecoder decoder = new VideoDecoder();
    private final AudioMixer mixer = new AudioMixer();
    private final BitrateConverter converter = new BitrateConverter();
    private final CodecFactory codecFactory = new CodecFactory();
    
    public File convertVideo(String filename, String format) {
        RawVideo raw = decoder.decode(filename);
        Audio audio = mixer.mix(raw);
        Codec codec = codecFactory.getCodec(format);
        EncodedVideo encoded = converter.convert(raw, codec.getBitrate());
        return saveToFile(encoded, format);
    }
}

// Client uses just the facade
VideoConversionFacade facade = new VideoConversionFacade();
File mp4 = facade.convertVideo("video.avi", "mp4");
```

### Facade Traps

**Q: Facade vs Service Layer pattern?**
A: Service Layer orchestrates business logic and transactions. Facade simplifies a technical subsystem. Service Layer is domain-oriented; Facade is structural.

**Q: Does Facade violate anything?**
A: Can violate SRP if it grows into a god class. Keep Facade thin — it delegates, doesn't implement.

---

## Adapter

**Intent**: Convert the interface of a class into another interface clients expect.

```java
// Target interface (what client expects)
public interface PaymentProcessor {
    boolean processPayment(String cardNumber, double amount, String currency);
}

// Adaptee — third-party library with incompatible interface
public class StripeAPI {
    public StripeCharge charge(StripeCard card, long amountInCents, String currency) { ... }
}

// Adapter — bridges the gap
public class StripeAdapter implements PaymentProcessor {
    private final StripeAPI stripeAPI;
    
    public StripeAdapter(StripeAPI stripeAPI) {
        this.stripeAPI = stripeAPI;
    }
    
    @Override
    public boolean processPayment(String cardNumber, double amount, String currency) {
        StripeCard card = new StripeCard(cardNumber);
        long amountInCents = (long) (amount * 100); // convert units
        StripeCharge charge = stripeAPI.charge(card, amountInCents, currency);
        return charge.isSuccessful();
    }
}

// Client uses PaymentProcessor — no StripeAPI dependency
PaymentProcessor processor = new StripeAdapter(new StripeAPI());
processor.processPayment("4111111111111111", 99.99, "USD");
```

### Adapter Traps

**Q: Object Adapter vs Class Adapter?**
A: Object Adapter (above) — uses composition. Class Adapter — uses multiple inheritance (`class StripeAdapter extends StripeAPI implements PaymentProcessor`). Java doesn't support multiple class inheritance, so Object Adapter is the Java way.

**Q: When do you choose Adapter over rewriting?**
A: When the adaptee is third-party (can't modify), or when the adaptee works fine but has a different interface. Adapter adds a layer but preserves existing code.

---

## Composite

**Intent**: Compose objects into tree structures to represent part-whole hierarchies. Treat individual objects and compositions uniformly.

```java
public interface FileSystemItem {
    String getName();
    long getSize();
    void print(String indent);
}

// Leaf
public class File implements FileSystemItem {
    private final String name;
    private final long size;
    
    public File(String name, long size) { this.name = name; this.size = size; }
    public String getName() { return name; }
    public long getSize() { return size; }
    public void print(String indent) { System.out.println(indent + name + " (" + size + " bytes)"); }
}

// Composite
public class Directory implements FileSystemItem {
    private final String name;
    private final List<FileSystemItem> children = new ArrayList<>();
    
    public Directory(String name) { this.name = name; }
    
    public void add(FileSystemItem item) { children.add(item); }
    public void remove(FileSystemItem item) { children.remove(item); }
    
    public String getName() { return name; }
    
    public long getSize() {
        return children.stream().mapToLong(FileSystemItem::getSize).sum(); // recursive!
    }
    
    public void print(String indent) {
        System.out.println(indent + name + "/");
        children.forEach(child -> child.print(indent + "  "));
    }
}

// Client treats File and Directory identically
Directory root = new Directory("root");
Directory src = new Directory("src");
src.add(new File("Main.java", 2048));
src.add(new File("Utils.java", 1024));
root.add(src);
root.add(new File("README.md", 512));
root.print(""); // recursive print
System.out.println("Total: " + root.getSize()); // recursive size
```

### Composite Traps

**Q: When should leaf and composite have different methods?**
A: Purist approach — same interface, leaf returns empty/zero for child operations. Pragmatic approach — `Component` interface only has common methods; `Directory` adds child management. Choose pragmatic when clients need to know if something is a directory.

**Q: How does Composite relate to Visitor?**
A: Visitor lets you add operations to a Composite tree without modifying the tree classes. Used together often (Composite for structure, Visitor for operations like serialize, render, calculate).

---

## Flyweight

**Intent**: Share common state between many fine-grained objects to save memory.

```java
// Without Flyweight — 1 million trees in a game, each stores type data
public class TreeWithoutFlyweight {
    private String type;     // "Oak", "Pine" — duplicated millions of times
    private String texture;  // 1MB texture — duplicated!
    private int x, y;        // unique per tree
}

// With Flyweight — shared state extracted
public class TreeType { // Flyweight — shared, immutable
    private final String name;
    private final String texture; // loaded once, shared
    
    public TreeType(String name, String texture) {
        this.name = name;
        this.texture = texture;
    }
    
    public void draw(int x, int y) {
        System.out.println("Drawing " + name + " at (" + x + "," + y + ") with " + texture);
    }
}

public class Tree { // Context — unique per instance
    private final int x, y;           // extrinsic state
    private final TreeType type;       // intrinsic state — shared reference
    
    public Tree(int x, int y, TreeType type) {
        this.x = x; this.y = y; this.type = type;
    }
    
    public void draw() { type.draw(x, y); }
}

public class TreeFactory { // Flyweight factory
    private static final Map<String, TreeType> cache = new HashMap<>();
    
    public static TreeType getTreeType(String name, String texture) {
        return cache.computeIfAbsent(name, k -> new TreeType(name, texture));
    }
}
```

### Flyweight Traps

**Q: Intrinsic vs Extrinsic state?**
A: Intrinsic = shared, context-independent (tree type, texture). Extrinsic = unique per instance (x, y coordinates). Flyweight stores only intrinsic; extrinsic is passed at runtime.

**Q: Real-world Flyweight in Java?**
A: `Integer.valueOf(-128 to 127)` — cached/shared. `String interning` — same literal returns same reference. Character data in word processors.

---

## Structural Patterns Summary

| Pattern | Intent | Key Signal |
|---|---|---|
| Decorator | Add behavior dynamically | "Wrap" with same interface |
| Proxy | Control access | Auth, caching, lazy-init |
| Facade | Simplify subsystem | Complex internals, simple API |
| Adapter | Bridge incompatible interfaces | Third-party integration |
| Composite | Tree/part-whole hierarchy | File systems, UI trees, org charts |
| Flyweight | Share to save memory | Millions of similar objects |
