/*
 * MediConecta: cambio de tema visual en tiempo real (estándar, claro y oscuro).
 *
 * Cambiar el atributo data-tema del <html> hace que el navegador recalcule al instante todas las variables
 * CSS de mediconecta.css: no se recarga la página ni se pierde lo que el usuario estaba haciendo. La
 * elección se guarda en una cookie (para que el servidor dibuje las próximas páginas ya con ese tema, sin
 * parpadeo) y en localStorage (para sincronizar otras pestañas abiertas).
 */
(function () {
  'use strict';

  var CLAVE = 'mediconecta-tema';
  var VALIDOS = ['estandar', 'claro', 'oscuro'];

  function marcarBotones(tema) {
    var botones = document.querySelectorAll('[data-tema-opcion]');
    for (var i = 0; i < botones.length; i++) {
      var activo = botones[i].getAttribute('data-tema-opcion') === tema;
      botones[i].classList.toggle('activo', activo);
      botones[i].setAttribute('aria-pressed', activo ? 'true' : 'false');
    }
  }

  function aplicar(tema, guardar) {
    if (VALIDOS.indexOf(tema) < 0) {
      tema = 'estandar';
    }
    var raiz = document.documentElement;
    raiz.classList.add('tema-cambiando');
    raiz.setAttribute('data-tema', tema);
    window.setTimeout(function () { raiz.classList.remove('tema-cambiando'); }, 300);
    marcarBotones(tema);
    if (guardar) {
      document.cookie = CLAVE + '=' + tema + '; path=/; max-age=31536000; SameSite=Lax';
      try { window.localStorage.setItem(CLAVE, tema); } catch (e) { /* almacenamiento no disponible */ }
    }
  }

  // Delegación: funciona también con los botones que PrimeFaces vuelve a dibujar por AJAX.
  document.addEventListener('click', function (evento) {
    var boton = evento.target.closest ? evento.target.closest('[data-tema-opcion]') : null;
    if (boton) {
      evento.preventDefault();
      aplicar(boton.getAttribute('data-tema-opcion'), true);
    }
  });

  // Si el usuario cambia el tema en otra pestaña, ésta lo sigue en vivo.
  window.addEventListener('storage', function (evento) {
    if (evento.key === CLAVE && evento.newValue) {
      aplicar(evento.newValue, false);
    }
  });

  document.addEventListener('DOMContentLoaded', function () {
    marcarBotones(document.documentElement.getAttribute('data-tema') || 'estandar');
  });

  window.MediConectaTema = { aplicar: function (tema) { aplicar(tema, true); } };
})();
