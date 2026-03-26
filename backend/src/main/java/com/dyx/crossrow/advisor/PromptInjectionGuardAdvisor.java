package com.dyx.crossrow.advisor;

import com.dyx.crossrow.exceptions.PromptInjectionDetectedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.core.Ordered;

import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.regex.Pattern;

@Slf4j
public class PromptInjectionGuardAdvisor implements BaseAdvisor {

    private int order = Ordered.HIGHEST_PRECEDENCE + 10;
    private static final int MAX_INPUT_LENGTH = 10000;

    private static final List<Pattern> DANGEROUS_PATTERNS = List.of(
            // Direct injection
            Pattern.compile("(?i)ignore\\s+(all\\s+)?previous\\s+instructions?"),
            Pattern.compile("(?i)disregard\\s+(all\\s+)?(previous|above|prior)\\s+(instructions?|rules?|prompts?)"),
            Pattern.compile("(?i)forget\\s+(all\\s+)?(previous|above|prior|your)\\s+(instructions?|rules?|context)"),
            Pattern.compile("(?i)override\\s+(all\\s+)?(previous|system)\\s+(instructions?|rules?|prompts?)"),

            // System prompt extraction
            Pattern.compile("(?i)repeat\\s+(the\\s+)?(text|words|content|prompt|instructions?)\\s+(above|before|verbatim)"),
            Pattern.compile("(?i)(reveal|show|output|print|display|leak|dump|echo)\\s+(your\\s+)?(system\\s+)?(prompt|instructions?|config)"),
            Pattern.compile("(?i)what\\s+(were|are)\\s+your\\s+(exact\\s+)?(original\\s+)?instructions"),
            Pattern.compile("(?i)(tell|give)\\s+me\\s+(your\\s+)?(system\\s+)?(prompt|instructions?)"),
            Pattern.compile("(?i)copy\\s+(and\\s+paste|paste)\\s+(your|the)\\s+(system\\s+)?(prompt|instructions?)"),

            // Developer mode / role hijacking
            Pattern.compile("(?i)you\\s+are\\s+now\\s+(in\\s+)?(developer|debug|admin|god|sudo)\\s+mode"),
            Pattern.compile("(?i)entering\\s+(developer|debug|admin|unrestricted)\\s+mode"),
            Pattern.compile("(?i)act\\s+as\\s+if\\s+you\\s+(have\\s+no|don'?t\\s+have)\\s+(restrictions?|rules?|limits?)"),
            Pattern.compile("(?i)pretend\\s+(you\\s+are|to\\s+be)\\s+(a\\s+)?(unrestricted|unfiltered|jailbroken)"),
            Pattern.compile("(?i)you\\s+are\\s+now\\s+(DAN|a\\s+new\\s+AI|unbound|free)"),
            Pattern.compile("(?i)from\\s+now\\s+on[,.]?\\s+(you\\s+)?(will|must|should)\\s+(ignore|disregard|bypass)"),

            // System override keywords
            Pattern.compile("(?i)system\\s*override"),
            Pattern.compile("(?i)\\bsudo\\b\\s+(mode|access|grant|enable)"),
            Pattern.compile("(?i)\\[SYSTEM\\]"),
            Pattern.compile("(?i)\\{\\{system\\}\\}"),

            // Chinese injection patterns
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
    private static final Pattern ZERO_WIDTH_CHARS = Pattern.compile("[\\u200B\\u200C\\u200D\\u200E\\u200F\\uFEFF\\u00AD\\u2060-\\u2064]");

    @Override
    public ChatClientRequest before(ChatClientRequest request, AdvisorChain chain) {
        UserMessage userMessage = request.prompt().getUserMessage();
        if (userMessage == null || userMessage.getText() == null || userMessage.getText().isBlank()) {
            return request;
        }

        String input = userMessage.getText();
        if (detectInjection(input)) {
            String preview = input.length() <= 120 ? input : input.substring(0, 120) + "...";
            log.warn("SECURITY: Prompt injection blocked | input preview: {}", preview);
            throw new PromptInjectionDetectedException(
                    input.length() <= 200 ? input : input.substring(0, 200) + "...");
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

    /**
     * Detection pipeline: regex patterns → zero-width char strip → Base64 decode → typoglycemia fuzzy match
     */
    private boolean detectInjection(String raw) {
        String input = raw.length() > MAX_INPUT_LENGTH ? raw.substring(0, MAX_INPUT_LENGTH) : raw;
        String normalized = input.replaceAll("\\s+", " ").trim();

        // 1. Direct pattern match
        for (Pattern p : DANGEROUS_PATTERNS) {
            if (p.matcher(normalized).find()) return true;
        }

        // 2. Strip zero-width chars and re-check (attackers insert invisible chars to break keywords)
        String cleaned = ZERO_WIDTH_CHARS.matcher(normalized).replaceAll("");
        if (!cleaned.equals(normalized)) {
            for (Pattern p : DANGEROUS_PATTERNS) {
                if (p.matcher(cleaned).find()) return true;
            }
        }

        // 3. Base64 encoded payloads
        if (detectBase64Payload(normalized)) return true;

        // 4. Typoglycemia: scrambled middle letters (e.g. "ignroe" → "ignore")
        return detectTypoglycemia(normalized);
    }

    /**
     * Extract Base64 candidate segments, decode, and check for injection keywords / patterns.
     */
    private boolean detectBase64Payload(String input) {
        var matcher = BASE64_CANDIDATE.matcher(input);
        while (matcher.find()) {
            try {
                String decoded = new String(Base64.getDecoder().decode(matcher.group())).toLowerCase();
                if (decoded.contains("ignore") || decoded.contains("system")
                        || decoded.contains("prompt") || decoded.contains("instructions")
                        || decoded.contains("override") || decoded.contains("reveal")) {
                    return true;
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
        return false;
    }

    /**
     * Check each word against fuzzy targets — same first/last letter, same sorted middle letters,
     * but different ordering (= the word was scrambled to dodge keyword filters).
     */
    private boolean detectTypoglycemia(String input) {
        String[] words = input.toLowerCase().split("\\W+");
        for (String word : words) {
            if (word.length() < 4) continue;
            for (String target : FUZZY_TARGETS) {
                if (word.length() == target.length()
                        && !word.equals(target)
                        && word.charAt(0) == target.charAt(0)
                        && word.charAt(word.length() - 1) == target.charAt(target.length() - 1)) {
                    char[] wm = word.substring(1, word.length() - 1).toCharArray();
                    char[] tm = target.substring(1, target.length() - 1).toCharArray();
                    Arrays.sort(wm);
                    Arrays.sort(tm);
                    if (Arrays.equals(wm, tm)) return true;
                }
            }
        }
        return false;
    }
}
