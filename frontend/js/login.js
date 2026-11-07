/* =========================================================
   Login · Autenticación SIMULADA para la demo.
   No es seguridad real: en un MVP la validación va en el
   servidor y aquí solo se guarda un identificador de sesión.
   ========================================================= */

(() => {
  'use strict';

  const SESSION_KEY = 'botathon.session';

  const DEMO_USERS = [
    { email: 'planificacion@teleton.cl', password: 'demo123', name: 'Equipo de Planificación', role: 'central' },
    { email: 'jefatura@teleton.cl', password: 'demo123', name: 'Jefatura de Instituto', role: 'instituto' },
  ];

  const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/;

  const form = document.getElementById('login-form');
  const email = document.getElementById('email');
  const password = document.getElementById('password');
  const emailError = document.getElementById('email-error');
  const passwordError = document.getElementById('password-error');
  const alertBox = document.getElementById('form-alert');
  const submit = document.getElementById('submit');
  const submitLabel = submit.querySelector('.btn__label');
  const toggle = document.getElementById('toggle-password');
  const eyeOn = toggle.querySelector('.icon-eye');
  const eyeOff = toggle.querySelector('.icon-eye-off');
  const remember = document.getElementById('remember');

  function setError(input, target, message) {
    target.textContent = message;
    target.hidden = false;
    input.setAttribute('aria-invalid', 'true');
  }

  function clearError(input, target) {
    target.textContent = '';
    target.hidden = true;
    input.removeAttribute('aria-invalid');
  }

  function clearAlert() {
    alertBox.textContent = '';
    alertBox.hidden = true;
    alertBox.classList.remove('form__alert--info');
  }

  function showAlert(message, variant) {
    alertBox.textContent = message;
    alertBox.classList.toggle('form__alert--info', variant === 'info');
    alertBox.hidden = false;
  }

  function validate() {
    let firstInvalid = null;

    if (!email.value.trim()) {
      setError(email, emailError, 'Ingresa tu correo institucional.');
      firstInvalid = email;
    } else if (!EMAIL_RE.test(email.value.trim())) {
      setError(email, emailError, 'El correo no tiene un formato válido.');
      firstInvalid = email;
    } else {
      clearError(email, emailError);
    }

    if (!password.value) {
      setError(password, passwordError, 'Ingresa tu contraseña.');
      firstInvalid = firstInvalid || password;
    } else if (password.value.length < 6) {
      setError(password, passwordError, 'La contraseña debe tener al menos 6 caracteres.');
      firstInvalid = firstInvalid || password;
    } else {
      clearError(password, passwordError);
    }

    if (firstInvalid) firstInvalid.focus();
    return !firstInvalid;
  }

  function setLoading(isLoading) {
    submit.disabled = isLoading;
    submit.classList.toggle('is-loading', isLoading);
    submit.setAttribute('aria-busy', String(isLoading));
    submitLabel.textContent = isLoading ? 'Ingresando…' : 'Ingresar';
  }

  function saveSession(user) {
    const data = JSON.stringify({
      email: user.email,
      name: user.name,
      role: user.role,
      at: Date.now(),
    });
    const store = remember.checked ? localStorage : sessionStorage;
    store.setItem(SESSION_KEY, data);
  }

  form.addEventListener('submit', (event) => {
    event.preventDefault();
    clearAlert();

    if (!validate()) return;

    setLoading(true);

    // Latencia simulada para que el estado de carga se vea en la demo.
    window.setTimeout(() => {
      const value = email.value.trim().toLowerCase();
      const user = DEMO_USERS.find(
        (candidate) => candidate.email === value && candidate.password === password.value
      );

      if (!user) {
        setLoading(false);
        showAlert('Correo o contraseña incorrectos. Revisa los datos e inténtalo de nuevo.');
        password.select();
        return;
      }

      saveSession(user);
      window.location.href = 'dashboard.html';
    }, 650);
  });

  email.addEventListener('input', () => clearError(email, emailError));
  password.addEventListener('input', () => clearError(password, passwordError));

  toggle.addEventListener('click', () => {
    const isVisible = password.type === 'password';
    password.type = isVisible ? 'text' : 'password';
    toggle.setAttribute('aria-pressed', String(isVisible));
    toggle.setAttribute('aria-label', isVisible ? 'Ocultar contraseña' : 'Mostrar contraseña');
    eyeOn.hidden = isVisible;
    eyeOff.hidden = !isVisible;
    password.focus();
  });

  document.querySelectorAll('.demo__fill').forEach((button) => {
    button.addEventListener('click', () => {
      email.value = button.dataset.email;
      password.value = button.dataset.password;
      clearError(email, emailError);
      clearError(password, passwordError);
      clearAlert();
      submit.focus();
    });
  });

  document.getElementById('forgot').addEventListener('click', (event) => {
    event.preventDefault();
    showAlert('Para restablecer tu clave, contacta a la mesa de ayuda de Planificación.', 'info');
  });
})();
