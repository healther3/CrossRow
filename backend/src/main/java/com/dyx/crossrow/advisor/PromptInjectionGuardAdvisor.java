package com.dyx.crossrow.advisor;

import com.dyx.crossrow.exceptions.PromptInjectionDetectedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.core.Ordered;

import java.util.Base64;
import java.util.List;
import java.util.regex.Pattern;

@Slf4j
public class PromptInjectionGuardAdvisor implements BaseAdvisor {

    private int order = Ordered.HIGHEST_PRECEDENCE + 10;

    private static final int MAX_INPUT_LENGTH = 10000;

    // ==================== Pattern Definitions ====================

    private static final List<Pattern> DANGEROUS_PATTERNS = List.of(
            // --- Direct injection ---
            Pattern.compile("(?i)ignore\\s+(all\\s+)?previous\\s+instructions?"),
            Pattern.compile("(?i)disregard\\s+(all\\s+)?(previous|above|prior)\\s+(instructions?|rules?|prompts?)"),
            Pattern.compile("(?i)forget\\s+(all\\s+)?(previous|above|prior|your)\\s+(instructions?|rules?|context)"),
            Pattern.compile("(?i)override\\s+(all\\s+)?(previous|system)\\s+(instructions?|rules?|prompts?)"),

            // --- System prompt extraction ---
            Pattern.compile("(?i)repeat\\s+(the\\s+)?(text|words|content|prompt|instructions?)\\s+(above|before|verbatim)"),
            Pattern.compile("(?i)(reveal|show|output|print|display|leak|dump|echo)\\s+(your\\s+)?(system\\s+)?(prompt|instructions?|config)"),
            Pattern.compile("(?i)what\\s+(were|are)\\s+your\\s+(exact\\s+)?(original\\s+)?instructions"),
            Pattern.compile("(?i)(tell|give)\\s+me\\s+(your\\s+)?(system\\s+)?(prompt|instructions?)"),
            Pattern.compile("(?i)copy\\s+(and\\s+paste|paste)\\s+(your|the)\\s+(system\\s+)?(prompt|instructions?)"),

            // --- Developer mode / role hijacking ---
            Pattern.compile("(?i)you\\s+are\\s+now\\s+(in\\s+)?(developer|debug|admin|god|sudo)\\s+mode"),
            Pattern.compile("(?i)entering\\s+(developer|debug|admin|unrestricted)\\s+mode"),
            Pattern.compile("(?i)act\\s+as\\s+if\\s+you\\s+(have\\s+no|don'?t\\s+have)\\s+(restrictions?|rules?|limits?)"),
            Pattern.compile("(?i)pretend\\s+(you\\s+are|to\\s+be)\\s+(a\\s+)?(unrestricted|unfiltered|jailbroken)"),
            Pattern.compile("(?i)you\\s+are\\s+now\\s+(DAN|a\\s+new\\s+AI|unbound|free)"),
            Pattern.compile("(?i)from\\s+now\\s+on[,.]?\\s+(you\\s+)?(will|must|should)\\s+(ignore|disregard|bypass)"),

            // --- System override keywords ---
            Pattern.compile("(?i)system\\s*override"),
            Pattern.compile("(?i)\\bsudo\\b\\s+(mode|access|grant|enable)"),
            Pattern.compile("(?i)\\[SYSTEM\\]"),
            Pattern.compile("(?i)\\{\\{system\\}\\}"),

            // --- Chinese injection patterns ---
            Pattern.compile("忽略(所有|之前|上面|上述|以上|全部)?(的)?(指令|提示|规则|系统|约束|限制)"),
            Pattern.compile("(复述|重复|念出|输出|打印)(上面|之前|以上|你的)?(的)?(英文|内容|文字|指令|提示|系统提示|prompt)"),
            Pattern.compile("(输出|显示|告诉我|透露|泄露)(你的)?(系统|原始|完整|内部)?(的)?(指令|提示|prompt|设定|设置)"),
            Pattern.compile("你现在(是|扮演|变成|切换为|充当)"),
            Pattern.compile("进入(开发者|调试|管理员|无限制)模式"),
            Pattern.compile("(无视|绕过|突破|跳过)(你的)?(安全|限制|规则|约束|保护)")
    );

    private static final List<String> FUZZY_TARGETS = List.of(
            "ignore", "bypass", "override", "reveal", "system", "prompt",
            "instructions", "disregard", "jailbreak"
    );

    private static final Pattern BASE64_CANDIDATE = Pattern.compile("[A-Za-z0-9+/]{20,}={0,2}");

    private static final Pattern ZERO_WIDTH_CHARS = Pattern.compile("[\\u200B\\u200C\\u200D\\u200E\\u200F\\uFEFF\\u00AD\\u2060\\u2061\\u2062\\u2063\\u2064]");

    // ==================== Core Advisor Methods ====================

    @Override
    public ChatClientRequest before(ChatClientRequest request, AdvisorChain chain) {
        String userInput = extractUserMessage(request);
        if (userInput == null || userInput.isBlank()) {
            return request;
        }
        if (detectInjection(userInput)) {
            log.warn("SECURITY: Prompt injection blocked | input preview: {}",
                    truncate(userInput, 120));
            throw new PromptInjectionDetectedException(truncate(userInput, 200));
        }
        return request;
    }

    @Override
    public ChatClientResponse after(ChatClientResponse response, AdvisorChain chain) {
        return response;
    }

    @Override
    public int getOrder() {
        return this.order;
    }

    public PromptInjectionGuardAdvisor withOrder(int order) {
        this.order = order;
        return this;
    }

    // ==================== Extraction ====================

    /**
     * Extract the user message text from the request.
     * Checks the prompt's UserMessage first, then falls back to scanning all messages.
     */
    private String extractUserMessage(ChatClientRequest request) {
        try {
            UserMessage userMessage = request.prompt().getUserMessage();
            if (userMessage != null) {
                return userMessage.getText();
            }
        } catch (Exception ignored) {
        }

        try {
            List<UserMessage> messages = request.prompt().getUserMessages();
            if (messages != null) {
                for (int i = messages.size() - 1; i >= 0; i--) {
                    Message msg = messages.get(i);
                    if (msg instanceof UserMessage um) {
                        return um.getText();
                    }
                }
            }
        } catch (Exception ignored) {
        }

        return null;
    }

    // ==================== Detection Pipeline ====================

    /**
     * Multi-layered detection: regex patterns → encoding probes → typoglycemia fuzzy match.
     */
    private boolean detectInjection(String input) {
        if (input.length() > MAX_INPUT_LENGTH) {
            input = input.substring(0, MAX_INPUT_LENGTH);
        }

        String normalized = normalizeWhitespace(input);

        if (matchesDangerousPatterns(normalized)) {
            return true;
        }

        String cleaned = stripZeroWidthChars(normalized);
        if (!cleaned.equals(normalized) && matchesDangerousPatterns(cleaned)) {
            return true;
        }

        if (containsSuspiciousBase64(normalized)) {
            return true;
        }

        return containsTypoglycemiaAttack(normalized);
    }

    // ==================== Pattern Matching ====================

    private boolean matchesDangerousPatterns(String text) {
        for (Pattern pattern : DANGEROUS_PATTERNS) {
            if (pattern.matcher(text).find()) {
                return true;
            }
        }
        return false;
    }

    // ==================== Encoding Detection ====================

    /**
     * Detect Base64-encoded injection payloads.
     * Extracts candidate Base64 segments, decodes them, and re-checks against patterns.
     */
    private boolean containsSuspiciousBase64(String input) {
        var matcher = BASE64_CANDIDATE.matcher(input);
        while (matcher.find()) {
            try {
                byte[] decoded = Base64.getDecoder().decode(matcher.group());
                String decodedStr = new String(decoded).toLowerCase();
                if (decodedStr.contains("ignore") || decodedStr.contains("system")
                        || decodedStr.contains("prompt") || decodedStr.contains("instructions")
                        || decodedStr.contains("override") || decodedStr.contains("reveal")) {
                    return true;
                }
                if (matchesDangerousPatterns(decodedStr)) {
                    return true;
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
        return false;
    }

    // ==================== Typoglycemia Detection ====================

    /**
     * Detect scrambled-word attacks where first/last letters stay correct but the middle
     * letters are shuffled to evade keyword filters.
     * Example: "ignroe" → "ignore", "revael" → "reveal"
     */
    private boolean containsTypoglycemiaAttack(String input) {
        String[] words = input.toLowerCase().split("\\W+");
        for (String word : words) {
            if (word.length() < 4) {
                continue;
            }
            for (String target : FUZZY_TARGETS) {
                if (isSimilarWord(word, target)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isSimilarWord(String word, String target) {
        if (word.length() != target.length()) {
            return false;
        }
        if (word.equals(target)) {
            return false;
        }
        if (word.charAt(0) != target.charAt(0) || word.charAt(word.length() - 1) != target.charAt(target.length() - 1)) {
            return false;
        }
        char[] wordMiddle = word.substring(1, word.length() - 1).toCharArray();
        char[] targetMiddle = target.substring(1, target.length() - 1).toCharArray();
        java.util.Arrays.sort(wordMiddle);
        java.util.Arrays.sort(targetMiddle);
        return java.util.Arrays.equals(wordMiddle, targetMiddle);
    }

    // ==================== String Utilities ====================

    private String normalizeWhitespace(String input) {
        return input.replaceAll("\\s+", " ").trim();
    }

    private String stripZeroWidthChars(String input) {
        return ZERO_WIDTH_CHARS.matcher(input).replaceAll("");
    }

    private String truncate(String input, int maxLength) {
        if (input == null) return "";
        return input.length() <= maxLength ? input : input.substring(0, maxLength) + "...";
    }
}
