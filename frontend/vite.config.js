import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  build: {
    // element-plus.js lớn (~800 kB) là bình thường với thư viện UI đầy đủ
    chunkSizeWarningLimit: 1000,
    // Gộp toàn bộ CSS thành 1 file
    cssCodeSplit: false,
    rolldownOptions: {
      output: {
        // dist/assets/js, dist/assets/css, dist/assets/img, dist/assets/fonts
        entryFileNames: 'assets/js/[name]-[hash].js',
        chunkFileNames: 'assets/js/[name]-[hash].js',
        assetFileNames: (asset) => {
          const name = asset.names?.[0] ?? ''
          if (name.endsWith('.css')) return 'assets/css/[name]-[hash][extname]'
          if (/\.(png|jpe?g|gif|svg|webp|ico)$/i.test(name)) return 'assets/img/[name]-[hash][extname]'
          if (/\.(woff2?|ttf|eot)$/i.test(name)) return 'assets/fonts/[name]-[hash][extname]'
          return 'assets/[name]-[hash][extname]'
        },
        // Gộp file: element-plus riêng, thư viện còn lại vào vendor, mọi trang vào pages
        codeSplitting: {
          groups: [
            { name: 'element-plus', test: /node_modules[\\/](element-plus|@element-plus)[\\/]/, priority: 30 },
            { name: 'vendor', test: /node_modules[\\/]/, priority: 20 },
            { name: 'pages', test: /src[\\/](views|layouts|components|composables)[\\/]/, priority: 10 }
          ]
        }
      }
    }
  },
  server: {
    port: 5173,
    // Chuyển tiếp /api sang backend Spring Boot -> không cần lo CORS khi phát triển
    proxy: {
      '/api': {
        target: process.env.API_TARGET || 'http://localhost:8080',
        changeOrigin: true,
        // Bỏ header Origin: request qua proxy là cùng nguồn với backend, nên chạy Vite ở cổng nào
        // (5173, 5174...) cũng không bị CORS của backend chặn
        configure: (proxy) => {
          proxy.on('proxyReq', (proxyReq) => proxyReq.removeHeader('origin'))
        }
      }
    }
  }
})
