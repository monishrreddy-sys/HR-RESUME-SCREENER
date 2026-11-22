
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;


// =====================================================
// 1. ENCAPSULATION: RESUME MODEL
// =====================================================

class Resume {

    private final String fileName;
    private final String content;
    private final String loadingError;

    public Resume(String fileName, String content) {
        this(fileName, content, null);
    }

    public Resume(String fileName, String content, String loadingError) {
        this.fileName = fileName;
        this.content = content;
        this.loadingError = loadingError;
    }

    public String getFileName() {
        return fileName;
    }

    public String getContent() {
        return content;
    }

    public String getLoadingError() {
        return loadingError;
    }
}


// =====================================================
// 2. ENCAPSULATION + COMPARABLE: CANDIDATE SCORE
// =====================================================

class CandidateScore implements Comparable<CandidateScore> {

    private final String fileName;
    private final int score;
    private final String justification;
    private final String scoringMethod;

    public CandidateScore(
            String fileName,
            int score,
            String justification,
            String scoringMethod
    ) {
        this.fileName = fileName;
        this.score = Math.max(0, Math.min(10, score));
        this.justification = justification;
        this.scoringMethod = scoringMethod;
    }

    public String getFileName() {
        return fileName;
    }

    public int getScore() {
        return score;
    }

    public String getJustification() {
        return justification;
    }

    public String getScoringMethod() {
        return scoringMethod;
    }

    @Override
    public int compareTo(CandidateScore other) {
        return Integer.compare(other.score, this.score);
    }
}


// =====================================================
// 3. ABSTRACTION: SCORING STRATEGY
// =====================================================

interface ScoringStrategy {

    CandidateScore evaluate(String jobDescription, Resume resume);
}


// =====================================================
// 4. RESUME VALIDATION
// =====================================================

class ResumeValidator {

    public static CandidateScore validate(Resume resume) {

        if (resume.getLoadingError() != null) {
            return new CandidateScore(
                    resume.getFileName(),
                    0,
                    "Resume could not be read: "
                            + resume.getLoadingError(),
                    "FILE ERROR"
            );
        }

        String content = resume.getContent().trim();

        if (content.isEmpty()) {
            return new CandidateScore(
                    resume.getFileName(),
                    0,
                    "Resume is empty. No information was available for evaluation.",
                    "EMPTY RESUME"
            );
        }

        // A single word such as "hi" is not meaningful resume content.
        String[] words = content.split("\\s+");

        if (words.length < 3) {
            return new CandidateScore(
                    resume.getFileName(),
                    0,
                    "Insufficient resume content. The file does not contain enough information for evaluation.",
                    "INVALID CONTENT"
            );
        }

        return null;
    }
}


// =====================================================
// 5. POLYMORPHISM: GEMINI AI SCORER
// =====================================================

class GeminiAIScorer implements ScoringStrategy {

    private final String apiKey;

    private final HttpClient httpClient;

    private static final int MAX_ATTEMPTS = 3;
    private static final int TIMEOUT_SECONDS = 5;
    private static final long RETRY_DELAY_MS = 500;

    public GeminiAIScorer(String apiKey) {

        this.apiKey = apiKey;

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .build();
    }

    private String resolveModel() {
        return "models/gemini-3.8-flash";
    }

    @Override
    public CandidateScore evaluate(
            String jobDescription,
            Resume resume
    ) {

        // Validate before making an API request
        CandidateScore validationResult =
                ResumeValidator.validate(resume);

        if (validationResult != null) {
            return validationResult;
        }

        try {

            String prompt =
                    "You are an HR Technical Recruiter. "
                    + "Compare the job description with the candidate resume.\n"
                    + "Evaluate technical skills, experience, education "
                    + "and relevance to the role.\n"
                    + "Do not invent qualifications.\n\n"
                    + "Reply ONLY in this exact format:\n"
                    + "SCORE: <number from 1 to 10>\n"
                    + "JUSTIFICATION: <exactly two concise sentences>\n\n"
                    + "JOB DESCRIPTION:\n"
                    + jobDescription
                    + "\n\nRESUME:\n"
                    + resume.getContent();

            String jsonBody =
                    "{\"contents\":[{\"parts\":[{\"text\":"
                    + toJsonString(prompt)
                    + "}]}]}";

            String url =
                    "https://generativelanguage.googleapis.com/v1beta/"
                    + resolveModel()
                    + ":generateContent?key=" + apiKey;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = null;

            // Maximum 3 attempts per resume
            for (int attempt = 1;
                 attempt <= MAX_ATTEMPTS;
                 attempt++) {

                response = null;

                try {

                    System.out.println(
                            "[Gemini] Evaluating "
                            + resume.getFileName()
                            + " | Attempt "
                            + attempt + "/" + MAX_ATTEMPTS
                    );

                    response = httpClient.send(
                            request,
                            HttpResponse.BodyHandlers.ofString()
                    );

                } catch (HttpTimeoutException e) {

                    System.out.println(
                            "[Timeout] Attempt "
                            + attempt + "/" + MAX_ATTEMPTS
                            + " exceeded 5 seconds."
                    );

                    if (attempt == MAX_ATTEMPTS) {
                        throw e;
                    }

                } catch (IOException e) {

                    System.out.println(
                            "[Network Error] Attempt "
                            + attempt + "/" + MAX_ATTEMPTS
                            + ": " + e.getMessage()
                    );

                    if (attempt == MAX_ATTEMPTS) {
                        throw e;
                    }
                }

                if (response != null) {

                    if (response.statusCode() == 200) {
                        break;
                    }

                    int status = response.statusCode();

                    boolean retryable =
                            status == 429 ||
                            status == 500 ||
                            status == 502 ||
                            status == 503 ||
                            status == 504;

                    if (!retryable || attempt == MAX_ATTEMPTS) {

                        throw new RuntimeException(
                                "Gemini API returned HTTP "
                                + status + ": " + response.body()
                        );
                    }

                    System.out.println(
                            "[Retry] Gemini returned HTTP "
                            + status + ". Retrying..."
                    );
                }

                if (attempt < MAX_ATTEMPTS) {
                    Thread.sleep(RETRY_DELAY_MS);
                }
            }

            if (response == null || response.statusCode() != 200) {
                throw new RuntimeException(
                        "Gemini failed after 3 attempts."
                );
            }

            String aiReply =
                    extractTextFromGeminiJson(response.body());

            if (aiReply.isBlank()) {
                throw new RuntimeException(
                        "Gemini returned an empty response."
                );
            }

            return parseAiReply(
                    resume.getFileName(),
                    aiReply
            );

        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();

            System.out.println(
                    "[Info] Gemini evaluation interrupted. "
                    + "Using offline fallback."
            );

            return new KeywordFallbackScorer()
                    .evaluate(jobDescription, resume);

        } catch (Exception e) {

            System.out.println(
                    "[Info] Gemini unavailable for "
                    + resume.getFileName()
                    + " (" + e.getMessage()
                    + "). Switching to offline fallback."
            );

            return new KeywordFallbackScorer()
                    .evaluate(jobDescription, resume);
        }
    }


    // Parse Gemini's score and justification
    private CandidateScore parseAiReply(
            String fileName,
            String reply
    ) {

        Matcher scoreMatcher =
                Pattern.compile("SCORE:\\s*(\\d{1,2})")
                        .matcher(reply);

        Matcher justificationMatcher =
                Pattern.compile(
                        "JUSTIFICATION:\\s*(.+)",
                        Pattern.DOTALL
                ).matcher(reply);

        if (!scoreMatcher.find() || !justificationMatcher.find()) {
            throw new RuntimeException(
                    "Gemini response did not follow the required format."
            );
        }

        int score = Integer.parseInt(scoreMatcher.group(1));

        if (score < 1 || score > 10) {
            throw new RuntimeException(
                    "Gemini returned an invalid score."
            );
        }

        String justification = justificationMatcher.group(1)
                .trim()
                .replaceAll("\\s+", " ");

        return new CandidateScore(
                fileName,
                score,
                justification,
                "GEMINI AI"
        );
    }


    // Safely escape prompt text for JSON
    private String toJsonString(String text) {

        StringBuilder result = new StringBuilder("\"");

        for (int i = 0; i < text.length(); i++) {

            char c = text.charAt(i);

            switch (c) {
                case '"':
                    result.append("\\\"");
                    break;

                case '\\':
                    result.append("\\\\");
                    break;

                case '\n':
                    result.append("\\n");
                    break;

                case '\r':
                    result.append("\\r");
                    break;

                case '\t':
                    result.append("\\t");
                    break;

                default:
                    if (c < 0x20) {
                        result.append(String.format("\\u%04x", (int) c));
                    } else {
                        result.append(c);
                    }
            }
        }

        result.append("\"");

        return result.toString();
    }


    // Extract JSON string value without external libraries
    private String extractTextFromGeminiJson(String json) {

        int keyPosition = json.indexOf("\"text\"");

        if (keyPosition < 0) {
            return "";
        }

        int colonPosition = json.indexOf(":", keyPosition);

        if (colonPosition < 0) {
            return "";
        }

        int i = colonPosition + 1;

        while (i < json.length()
                && Character.isWhitespace(json.charAt(i))) {
            i++;
        }

        if (i >= json.length() || json.charAt(i) != '"') {
            return "";
        }

        i++;

        StringBuilder result = new StringBuilder();

        while (i < json.length()) {

            char c = json.charAt(i++);

            if (c == '"') {
                return result.toString();
            }

            if (c == '\\') {

                if (i >= json.length()) {
                    return "";
                }

                char escaped = json.charAt(i++);

                switch (escaped) {

                    case '"':
                        result.append('"');
                        break;

                    case '\\':
                        result.append('\\');
                        break;

                    case '/':
                        result.append('/');
                        break;

                    case 'n':
                        result.append('\n');
                        break;

                    case 'r':
                        result.append('\r');
                        break;

                    case 't':
                        result.append('\t');
                        break;

                    case 'b':
                        result.append('\b');
                        break;

                    case 'f':
                        result.append('\f');
                        break;

                    case 'u':

                        if (i + 4 > json.length()) {
                            return "";
                        }

                        try {
                            result.append((char) Integer.parseInt(
                                    json.substring(i, i + 4), 16
                            ));
                        } catch (NumberFormatException e) {
                            return "";
                        }

                        i += 4;
                        break;

                    default:
                        return "";
                }

            } else {
                result.append(c);
            }
        }

        return "";
    }
}


// =====================================================
// 6. POLYMORPHISM: OFFLINE KEYWORD SCORER
// =====================================================

class KeywordFallbackScorer implements ScoringStrategy {

    private static final String[] KEY_SKILLS = {
            "java",
            "oop",
            "collections",
            "exception handling",
            "sql",
            "mysql",
            "spring boot",
            "rest api",
            "git",
            "junit"
    };

    private boolean containsWholeWord(
            String text,
            String keyword
    ) {

        return Pattern.compile(
                "\\b" + Pattern.quote(keyword) + "\\b"
        ).matcher(text).find();
    }

    @Override
    public CandidateScore evaluate(
            String jobDescription,
            Resume resume
    ) {

        // Validate before scoring
        CandidateScore validationResult =
                ResumeValidator.validate(resume);

        if (validationResult != null) {
            return validationResult;
        }

        String jdLower =
                jobDescription.toLowerCase(Locale.ROOT);

        String resLower =
                resume.getContent().toLowerCase(Locale.ROOT);

        List<String> matched = new ArrayList<>();
        List<String> missing = new ArrayList<>();

        for (String skill : KEY_SKILLS) {

            if (containsWholeWord(jdLower, skill)) {

                if (containsWholeWord(resLower, skill)) {
                    matched.add(skill.toUpperCase(Locale.ROOT));
                } else {
                    missing.add(skill.toUpperCase(Locale.ROOT));
                }
            }
        }

        int totalRequired = matched.size() + missing.size();

        int score = totalRequired == 0
                ? 5
                : (int) Math.round(
                        matched.size() * 10.0 / totalRequired
                );

        // Java is a mandatory skill for this particular role
        if (!containsWholeWord(resLower, "java")) {
            score = Math.min(score, 3);
        }

        score = Math.max(1, score);

        String sentence1 = matched.isEmpty()
                ? "The candidate lacks the core Java and backend skills listed in the job description."
                : "Demonstrates familiarity with key required skills including "
                        + String.join(", ", matched) + ".";

        String sentence2 = missing.isEmpty()
                ? "Meets all primary technical requirements and is recommended for further screening."
                : "Missing explicit mention of "
                        + String.join(", ", missing)
                        + ", which should be verified during screening.";

        return new CandidateScore(
                resume.getFileName(),
                score,
                sentence1 + " " + sentence2,
                "OFFLINE KEYWORD"
        );
    }
}


// =====================================================
// 7. MAIN APPLICATION CONTROLLER
// =====================================================

public class ResumeScreenerApp {

    public static void main(String[] args) {

        Path jdPath = Paths.get("job_description.txt");
        Path resumesDir = Paths.get("resumes");

        try {

            // Load job description
            String jobDescription =
                    Files.readString(jdPath, StandardCharsets.UTF_8);

            // Load all resume files
            List<Resume> resumes =
                    loadResumes(resumesDir);

            // Read Gemini API key
            String apiKey = System.getenv("GEMINI_API_KEY");

            ScoringStrategy scorer =
                    (apiKey != null && !apiKey.isBlank())
                    ? new GeminiAIScorer(apiKey)
                    : new KeywordFallbackScorer();

            System.out.println("\n=== HR RESUME SCREENER ===");

            System.out.println(
                    "Scoring Engine: "
                    + scorer.getClass().getSimpleName()
            );

            System.out.println(
                    "Resumes Found: " + resumes.size()
            );

            System.out.println(
                    "Supported Format: TXT\n"
            );

            List<CandidateScore> results = new ArrayList<>();

            // Evaluate every discovered resume
            for (Resume resume : resumes) {

                System.out.println(
                        "\n[Processing] " + resume.getFileName()
                );

                CandidateScore result =
                        scorer.evaluate(jobDescription, resume);

                results.add(result);
            }

            // Sort candidates by descending score
            Collections.sort(results);

            System.out.println(
                    "\n========== FINAL SHORTLIST ==========\n"
            );

            int rank = 1;

            for (CandidateScore candidate : results) {

                String status = candidate.getScore() >= 7
                        ? "SHORTLISTED"
                        : "REJECTED";

                System.out.printf(
                        "#%d | %-22s | Score: %d/10 [%s]%n",
                        rank++,
                        candidate.getFileName(),
                        candidate.getScore(),
                        status
                );

                System.out.println(
                        "    Method: "
                        + candidate.getScoringMethod()
                );

                System.out.println(
                        "    " + candidate.getJustification()
                        + "\n"
                );
            }

            // Export every result to CSV
            saveCsvReport(
                    Paths.get("shortlist_report.csv"),
                    results
            );

            System.out.println(
                    "Total Resumes Processed: " + results.size()
            );

            System.out.println(
                    "Saved full report to: shortlist_report.csv"
            );

        } catch (IOException e) {

            System.err.println(
                    "File Error: " + e.getMessage()
            );
        }
    }


    // =================================================
    // 8. DYNAMIC RESUME LOADING
    // =================================================

    private static List<Resume> loadResumes(Path folder)
            throws IOException {

        List<Resume> resumes = new ArrayList<>();

        // Create folder automatically if it does not exist
        if (!Files.exists(folder)) {
            Files.createDirectories(folder);
        }

        try (Stream<Path> files = Files.list(folder)) {

            List<Path> resumeFiles = files
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName()
                            .toString()
                            .toLowerCase(Locale.ROOT)
                            .endsWith(".txt"))
                    .sorted()
                    .collect(Collectors.toList());

            for (Path file : resumeFiles) {

                String fileName =
                        file.getFileName().toString();

                try {

                    String content = Files.readString(
                            file,
                            StandardCharsets.UTF_8
                    );

                    resumes.add(
                            new Resume(fileName, content)
                    );

                } catch (IOException e) {

                    // Keep unreadable files in the final report
                    System.out.println(
                            "[Warning] Could not read "
                            + fileName + ": " + e.getMessage()
                    );

                    resumes.add(
                            new Resume(
                                    fileName,
                                    "",
                                    e.getMessage()
                            )
                    );
                }
            }
        }

        return resumes;
    }


    // =================================================
    // 9. CSV REPORT GENERATION
    // =================================================

    private static void saveCsvReport(
            Path outPath,
            List<CandidateScore> results
    ) throws IOException {

        List<String> lines = new ArrayList<>();

        lines.add(
                "Rank,File Name,Score (1-10),Status,"
                + "Scoring Method,Justification"
        );

        int rank = 1;

        for (CandidateScore candidate : results) {

            String status = candidate.getScore() >= 7
                    ? "SHORTLISTED"
                    : "REJECTED";

            String line =
                    rank++ + ","
                    + csvEscape(candidate.getFileName()) + ","
                    + candidate.getScore() + ","
                    + csvEscape(status) + ","
                    + csvEscape(candidate.getScoringMethod()) + ","
                    + csvEscape(candidate.getJustification());

            lines.add(line);
        }

        Files.write(
                outPath,
                lines,
                StandardCharsets.UTF_8
        );
    }


    // Escape CSV fields safely
    private static String csvEscape(String value) {

        if (value == null) {
            value = "";
        }

        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
