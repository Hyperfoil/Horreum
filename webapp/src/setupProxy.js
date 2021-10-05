/* eslint-disable */
const proxy = require('http-proxy-middleware')

module.exports = function(app){
    const useHttps = process.env.HTTPS
    const port = useHttps ? 8443 : 8080
    app.use(proxy('/api',{target: (useHttps ? 'https': 'http') + '://localhost:' + port + '/', secure: false}))
    app.use(proxy('/ws', {target: 'ws://localhost:' + port + '/',ws:true}))
}