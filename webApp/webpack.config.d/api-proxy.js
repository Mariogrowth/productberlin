// Optional fast UI development: run `npm run dev` (Worker) in another terminal.
config.devServer = config.devServer || {};
config.devServer.proxy = [{ context: ['/api'], target: 'http://127.0.0.1:8787' }];
