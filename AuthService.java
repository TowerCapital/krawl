import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Base64;
import java.util.Optional;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Service managing local user authentication and registration.
 *
 * Security specifications:
 * <ul>
 *   <li>Algorithm: PBKDF2WithHmacSHA256</li>
 *   <li>Salt: Cryptographically secure 16-byte random salt generated via {@link SecureRandom}</li>
 *   <li>Key length: 256 bits</li>
 *   <li>Default iterations: 100,000</li>
 *   <li>Comparison: Constant-time byte comparison via {@link MessageDigest#isEqual(byte[], byte[])}</li>
 *   <li>Credentials storage: Only password hash, salt, and iteration count are stored in MySQL</li>
 * </ul>
 *
 * Authentication is strictly local to each device's database; no external or cloud auth is used.
 */
public class AuthService {

    public static final String ALGORITHM          = "PBKDF2WithHmacSHA256";
    public static final int    SALT_BYTE_LENGTH   = 16;
    public static final int    HASH_BIT_LENGTH    = 256;
    public static final int    DEFAULT_ITERATIONS = 100000;

    private final UserRepository userRepository;
    private final SecureRandom   secureRandom;

    public AuthService(UserRepository userRepository) {
        if (userRepository == null) {
            throw new IllegalArgumentException("UserRepository cannot be null.");
        }
        this.userRepository = userRepository;
        this.secureRandom   = new SecureRandom();
    }

    /**
     * Registers a new local user with salted PBKDF2 hash.
     *
     * @param username human-readable username
     * @param password plaintext password (cleared from memory after hashing)
     * @return the registered {@link User} entity
     * @throws AuthException if username is invalid or already taken
     * @throws SQLException on database errors
     */
    public User register(String username, String password) throws AuthException, SQLException {
        if (username == null || username.trim().isEmpty()) {
            throw new AuthException("Username cannot be empty.");
        }
        if (password == null || password.isEmpty()) {
            throw new AuthException("Password cannot be empty.");
        }

        String normalizedUsername = username.trim();
        String userId = normalizedUsername.toLowerCase(); // Map username directly to local identity

        // Check for existing username or userId
        if (userRepository.findByUsername(normalizedUsername).isPresent() || userRepository.findById(userId).isPresent()) {
            throw new AuthException("Username '" + normalizedUsername + "' is already registered on this device.");
        }

        // Generate cryptographically secure random salt
        byte[] saltBytes = new byte[SALT_BYTE_LENGTH];
        secureRandom.nextBytes(saltBytes);
        String saltBase64 = Base64.getEncoder().encodeToString(saltBytes);

        // Compute PBKDF2 hash
        String hashBase64 = hashPassword(password.toCharArray(), saltBytes, DEFAULT_ITERATIONS);

        User newUser = new User(
                userId,
                normalizedUsername,
                hashBase64,
                saltBase64,
                DEFAULT_ITERATIONS,
                new Timestamp(System.currentTimeMillis())
        );

        boolean registered = userRepository.register(newUser);
        if (!registered) {
            throw new AuthException("Registration failed: username or ID conflict.");
        }

        return newUser;
    }

    /**
     * Authenticates an existing local user.
     *
     * @param username human-readable username
     * @param password plaintext password
     * @return the authenticated {@link User} entity
     * @throws AuthException if credentials are invalid or user not found
     * @throws SQLException on database errors
     */
    public User login(String username, String password) throws AuthException, SQLException {
        if (username == null || username.trim().isEmpty() || password == null || password.isEmpty()) {
            throw new AuthException("Invalid username or password.");
        }

        String normalizedUsername = username.trim();
        Optional<User> userOpt = userRepository.findByUsername(normalizedUsername);

        if (!userOpt.isPresent()) {
            // Check by userId as alternative
            userOpt = userRepository.findById(normalizedUsername.toLowerCase());
        }

        if (!userOpt.isPresent()) {
            throw new AuthException("Invalid username or password.");
        }

        User user = userOpt.get();
        byte[] saltBytes;
        try {
            saltBytes = Base64.getDecoder().decode(user.getPasswordSalt());
        } catch (IllegalArgumentException e) {
            throw new AuthException("Corrupt authentication data for user.", e);
        }

        String computedHash = hashPassword(password.toCharArray(), saltBytes, user.getPasswordIterations());
        byte[] computedBytes = Base64.getDecoder().decode(computedHash);
        byte[] storedBytes   = Base64.getDecoder().decode(user.getPasswordHash());

        // Constant-time comparison to prevent timing attacks
        if (!MessageDigest.isEqual(computedBytes, storedBytes)) {
            throw new AuthException("Invalid username or password.");
        }

        return user;
    }

    /**
     * Hashes a password using PBKDF2WithHmacSHA256 and the provided salt.
     */
    private String hashPassword(char[] password, byte[] salt, int iterations) throws AuthException {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, HASH_BIT_LENGTH);
        try {
            SecretKeyFactory skf = SecretKeyFactory.getInstance(ALGORITHM);
            byte[] hash = skf.generateSecret(spec).getEncoded();
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new AuthException("Failed to hash password with algorithm " + ALGORITHM, e);
        } finally {
            spec.clearPassword();
        }
    }
}

