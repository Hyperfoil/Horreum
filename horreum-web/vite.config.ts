import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import viteTsconfigPaths from 'vite-tsconfig-paths'

export default defineConfig({
    // depending on your application, base can also be "/"
    base: '/',
    plugins: [react(), viteTsconfigPaths()],
    server: {    
        // this ensures that the browser DOES NOT open upon server start
        open: false,
        // this sets a default port to 3000  
        port: 3000, 

        proxy: {
            '^/api/.*': 'http://localhost:8080'
        }
    },
})
