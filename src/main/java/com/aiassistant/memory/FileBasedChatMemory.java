package com.aiassistant.memory;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import org.objenesis.strategy.StdInstantiatorStrategy;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class FileBasedChatMemory implements ChatMemory {

    private final String baseDir;
    private static final Kryo kryo = new Kryo();

    static {
        kryo.setRegistrationRequired(false);
        kryo.setInstantiatorStrategy(new StdInstantiatorStrategy());
    }

    public FileBasedChatMemory(String dir) {
        this.baseDir = dir;
        File baseDir = new File(dir);
        if (!baseDir.exists()) {
            baseDir.mkdirs();
        }
    }

    @Override
    public void add(String conversationId, List<Message> messages) {
        List<Message> conversationMessages = getOrCreateConversation(conversationId);
        conversationMessages.addAll(messages);
        saveConversation(conversationId, conversationMessages);
    }

    @Override
    public List<Message> get(String conversationId) {
        return getOrCreateConversation(conversationId);
    }

    @Override
    public void clear(String conversationId) {
        File file = getConversationFile(conversationId);
        if (file.exists()) {
            file.delete();
        }
    }

    @SuppressWarnings("unchecked")
    private List<Message> getOrCreateConversation(String conversationId) {
        File file = getConversationFile(conversationId);
        if (!file.exists()) {
            return new ArrayList<>();
        }
        try (Input input = new Input(new FileInputStream(file))) {
            return (List<Message>) kryo.readClassAndObject(input);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private void saveConversation(String conversationId, List<Message> messages) {
        File file = getConversationFile(conversationId);
        try (Output output = new Output(new FileOutputStream(file))) {
            kryo.writeClassAndObject(output, messages);
        } catch (Exception e) {
            throw new RuntimeException("Failed to save conversation", e);
        }
    }

    private File getConversationFile(String conversationId) {
        return new File(baseDir, conversationId + ".dat");
    }
}
