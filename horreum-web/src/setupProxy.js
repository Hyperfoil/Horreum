/* eslint-disable */
const { createProxyMiddleware } = require('http-proxy-middleware');

module.exports = function(app){
    const useHttps = process.env.HTTPS
    const port = useHttps ? 8443 : 8080
    app.use(createProxyMiddleware('/api',{target: (useHttps ? 'https': 'http') + '://localhost:' + port + '/', secure: false}))
}