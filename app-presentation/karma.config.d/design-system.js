// Run component assertions against the same stylesheet shipped by webApp.
const path = require('path');
const resources = path.resolve(config.basePath, '../../../../app-presentation/src/jsMain/resources');
config.files.push({ pattern: path.join(resources, 'design-system.css'), included: true, watched: true, type: 'css' });
config.files.push({ pattern: path.join(resources, 'fonts/*.woff2'), included: false, served: true });
config.proxies['/fonts/'] = '/absolute' + path.join(resources, 'fonts/') ;
