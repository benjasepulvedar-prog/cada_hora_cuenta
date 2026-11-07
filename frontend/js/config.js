const localHost = ['localhost', '127.0.0.1'].includes(window.location.hostname);

// For a separately hosted frontend, replace this with the deployed HTTPS API URL.
window.CHC_CONFIG = Object.freeze({
  apiBaseUrl: localHost ? 'http://localhost:8080/api' : `${window.location.origin}/api`
});
