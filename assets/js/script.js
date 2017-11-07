var jekyllBootstrapDoc = {
  buildSideMenu: function() {
    var html = '';

    $('.bs-docs-section').each(function() {
      var h1 = $(this).find('h1[id]').first(),
        h2 = $(this).find('h2[id]');

      if (h1.length) {
        html += '<li><a href="#' + h1[0].id + '">' + h1.clone().children().remove().end().text() + '</a>';

        if (h2.length) {
          html += '<ul class="nav">';
          h2.each(function() {
            html += '<li><a href="#' + this.id + '">' + $(this).clone().children().remove().end().text() + '</a></li>';
          });
          html += '</ul>';
        }

        html += '</li>';
      }
    });

    if (html != '') {
      $('.bs-docs-sidenav').html(html);
    }
  },

  addHeadingAnchors: function() {
    $('h1[id], h2[id], h3[id]').each(function() {
      if ($(this).children('.anchor-link').length === 0) {
        $(this).prepend('<a href="#' + this.id + '" class="anchor-link"><span class="glyphicon glyphicon-link" aria-hidden="true"></span></a>');
      }
    });
  },
};

$(function() {
  jekyllBootstrapDoc.buildSideMenu();
  jekyllBootstrapDoc.addHeadingAnchors();
});
