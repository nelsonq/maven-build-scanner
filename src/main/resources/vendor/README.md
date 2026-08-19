# Vendored assets

Third-party assets the report page loads. `index.html` fetches them from their CDNs; `Exporter`
inlines these copies instead, so an exported report renders without network access.

The versions match the CDN URLs in `index.html`, and the four with an `integrity` attribute there
were checked against it after downloading:

| File | Version | Source | Licence |
| --- | --- | --- | --- |
| `jquery-3.3.1.min.js` | 3.3.1 | https://code.jquery.com/jquery-3.3.1.min.js | MIT |
| `bootstrap-4.1.1.min.css` | 4.1.1 | https://stackpath.bootstrapcdn.com/bootstrap/4.1.1/css/bootstrap.min.css | MIT |
| `bootstrap-4.1.1.min.js` | 4.1.1 | https://stackpath.bootstrapcdn.com/bootstrap/4.1.1/js/bootstrap.min.js | MIT |
| `popper-1.14.3.min.js` | 1.14.3 | https://cdnjs.cloudflare.com/ajax/libs/popper.js/1.14.3/umd/popper.min.js | MIT |
| `feather-icons-4.29.2.min.js` | 4.29.2 | https://unpkg.com/feather-icons/dist/feather.min.js | MIT |

`index.html` requests feather-icons without a version, so the CDN serves whatever is current while
this copy is pinned; the others are pinned on both sides.

Google Charts is not vendored - `loader.js` fetches the rest of itself at runtime, so an exported
report still needs network access for the timeline and the pie chart.
