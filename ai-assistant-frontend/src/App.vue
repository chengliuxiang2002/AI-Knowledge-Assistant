<template>
  <div class="ai-assistant-widget">
    <header class="widget-header">
      <div class="header-left">
        <span class="logo">AI</span>
        <h1>Knowledge Assistant</h1>
      </div>
      <div class="header-right">
        <label class="rag-toggle" title="Enable knowledge base (RAG) retrieval">
          <input type="checkbox" v-model="ragEnabled" />
          <span class="toggle-label">RAG</span>
        </label>
      </div>
    </header>

    <div class="chat-messages" ref="msgContainer">
      <div v-if="messages.length === 0" class="welcome">
        <div class="welcome-icon">AI</div>
        <p>Ask me anything about the knowledge base or any topic.</p>
        <p class="welcome-hint">Toggle RAG to search indexed documents.</p>
      </div>

      <div v-for="(msg, i) in messages" :key="i"
           :class="['message', msg.role]">
        <div class="avatar">{{ msg.role === 'user' ? 'U' : 'AI' }}</div>
        <div class="bubble">
          <div class="content">{{ msg.content }}</div>
          <span v-if="loading && i === messages.length - 1 && msg.role === 'assistant'"
                class="cursor-blink">|</span>
        </div>
      </div>
    </div>

    <div class="chat-input-area">
      <textarea
        v-model="input"
        @keydown.enter.exact.prevent="send"
        placeholder="Ask a question..."
        rows="2"
        :disabled="loading"
      ></textarea>
      <button @click="send" :disabled="loading || !input.trim()" class="send-btn">
        <span v-if="!loading">Send</span>
        <span v-else>Sending...</span>
      </button>
    </div>
  </div>
</template>

<script setup>
import { ref, nextTick, watch } from 'vue'

const messages = ref([])
const input = ref('')
const loading = ref(false)
const ragEnabled = ref(false)
const msgContainer = ref(null)

const API_BASE = import.meta.env.PROD ? '/api' : '/api'

const chatId = 'widget-' + Math.random().toString(36).slice(2, 10)

function scrollBottom() {
  nextTick(() => {
    if (msgContainer.value) {
      msgContainer.value.scrollTop = msgContainer.value.scrollHeight
    }
  })
}

async function send() {
  const text = input.value.trim()
  if (!text || loading.value) return
  input.value = ''
  messages.value.push({ role: 'user', content: text })
  messages.value.push({ role: 'assistant', content: '' })
  loading.value = true
  scrollBottom()

  const endpoint = ragEnabled.value
    ? `${API_BASE}/api/assistant/chat/rag/stream`
    : `${API_BASE}/api/assistant/chat/stream`

  const url = `${endpoint}?message=${encodeURIComponent(text)}&chatId=${chatId}`

  try {
    const eventSource = new EventSource(url)
    const lastMsg = messages.value[messages.value.length - 1]

    eventSource.onmessage = (event) => {
      lastMsg.content += event.data
      scrollBottom()
    }

    eventSource.onerror = () => {
      eventSource.close()
      loading.value = false
      if (!lastMsg.content) {
        lastMsg.content = 'Sorry, an error occurred. Please try again.'
      }
    }
  } catch (e) {
    loading.value = false
    messages.value[messages.value.length - 1].content = 'Connection failed. Is the server running?'
  }
}
</script>

<style scoped>
.ai-assistant-widget {
  display: flex;
  flex-direction: column;
  max-width: 720px;
  margin: 0 auto;
  height: 100vh;
  max-height: 600px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 2px 16px rgba(0,0,0,0.08);
  overflow: hidden;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
}

.widget-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 20px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: #fff;
  flex-shrink: 0;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.logo {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 8px;
  background: rgba(255,255,255,0.2);
  font-weight: 700;
  font-size: 14px;
}

.header-left h1 {
  font-size: 16px;
  font-weight: 600;
  margin: 0;
}

.rag-toggle {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  font-size: 13px;
  opacity: 0.9;
}

.rag-toggle input {
  width: 16px;
  height: 16px;
  accent-color: #fff;
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  background: #f8f9fa;
}

.welcome {
  text-align: center;
  padding: 40px 20px;
  color: #999;
}

.welcome-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 56px;
  height: 56px;
  border-radius: 14px;
  background: linear-gradient(135deg, #667eea, #764ba2);
  color: #fff;
  font-weight: 700;
  font-size: 20px;
  margin-bottom: 16px;
}

.welcome p {
  margin: 0;
  line-height: 1.6;
}

.welcome-hint {
  font-size: 13px;
  color: #bbb;
  margin-top: 6px !important;
}

.message {
  display: flex;
  gap: 10px;
  margin-bottom: 16px;
}

.message.user {
  flex-direction: row-reverse;
}

.avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 700;
  flex-shrink: 0;
  color: #fff;
}

.message.user .avatar {
  background: #4a90d9;
}

.message.assistant .avatar {
  background: linear-gradient(135deg, #667eea, #764ba2);
}

.bubble {
  max-width: 75%;
  padding: 10px 14px;
  border-radius: 12px;
  font-size: 14px;
  line-height: 1.6;
  word-wrap: break-word;
  white-space: pre-wrap;
}

.message.user .bubble {
  background: #4a90d9;
  color: #fff;
  border-bottom-right-radius: 4px;
}

.message.assistant .bubble {
  background: #fff;
  color: #333;
  border-bottom-left-radius: 4px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.06);
}

.cursor-blink {
  animation: blink 1s infinite;
  color: #764ba2;
}

@keyframes blink {
  0%, 50% { opacity: 1; }
  51%, 100% { opacity: 0; }
}

.chat-input-area {
  display: flex;
  gap: 10px;
  padding: 12px 16px;
  border-top: 1px solid #eee;
  background: #fff;
  flex-shrink: 0;
}

.chat-input-area textarea {
  flex: 1;
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  padding: 10px 12px;
  font-size: 14px;
  resize: none;
  outline: none;
  font-family: inherit;
  transition: border-color 0.2s;
}

.chat-input-area textarea:focus {
  border-color: #667eea;
}

.send-btn {
  padding: 10px 20px;
  background: linear-gradient(135deg, #667eea, #764ba2);
  color: #fff;
  border: none;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: opacity 0.2s;
  white-space: nowrap;
}

.send-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.send-btn:not(:disabled):hover {
  opacity: 0.9;
}

@media (max-width: 480px) {
  .ai-assistant-widget {
    max-height: 100vh;
    border-radius: 0;
  }
  .bubble {
    max-width: 85%;
  }
}
</style>
