package com.social.service.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 敏感词过滤器（DFA 字典树，阶段七内容审核）。
 *
 * <p>词库来自 classpath {@code sensitive-words.txt}（每行一个词，{@code #} 开头为注释）。
 * 策略：动态/评论命中 → 动态置为"待审核"（先发后审，仅作者可见），评论直接拦截。
 * 后续可替换为阿里云内容安全 API（见 AppProperties / 集成说明）。
 */
@Slf4j
@Component
public class SensitiveWordFilter {

    private static final String WORD_FILE = "sensitive-words.txt";

    private final TrieNode root = new TrieNode();

    @PostConstruct
    public void init() {
        try (InputStream in = new ClassPathResource(WORD_FILE).getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            int count = 0;
            while ((line = reader.readLine()) != null) {
                String word = line.trim();
                if (word.isEmpty() || word.startsWith("#")) {
                    continue;
                }
                addWord(word);
                count++;
            }
            log.info("敏感词库加载完成，共 {} 词", count);
        } catch (Exception e) {
            log.warn("敏感词库加载失败（不影响启动）：{}", e.getMessage());
        }
    }

    private void addWord(String word) {
        TrieNode node = root;
        for (int i = 0; i < word.length(); i++) {
            node = node.children.computeIfAbsent(word.charAt(i), k -> new TrieNode());
        }
        node.end = true;
    }

    /** 文本是否命中任一敏感词。 */
    public boolean contains(String text) {
        if (text == null || text.isEmpty() || root.children.isEmpty()) {
            return false;
        }
        String t = text.toLowerCase();
        for (int i = 0; i < t.length(); i++) {
            TrieNode node = root;
            for (int j = i; j < t.length(); j++) {
                node = node.children.get(t.charAt(j));
                if (node == null) {
                    break;
                }
                if (node.end) {
                    return true;
                }
            }
        }
        return false;
    }

    private static class TrieNode {
        private final Map<Character, TrieNode> children = new HashMap<>();
        private boolean end = false;
    }
}
