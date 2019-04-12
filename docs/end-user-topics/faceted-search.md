# Faceted Search

Dockstore now offers faceted search, which allows for flexible querying of tools and workflows. Tabs are used to split up the results between tools and workflows. You can search for basic terms/phrases, filter using facets, and also use advanced search queries. Note that using advanced search after setting a basic search will override your basic search, and vice versa.

## Filtering Your Search
### Basic Search
If you want to do a basic search for a keyword or a collection of keywords, use the search box present on the left side of the page. This will search for matches that contain at least one of the keywords. Keywords should be separated by spaces.

![Basic Search](/assets/images/docs/search-basic.png)

### Advanced Search
Advanced search is useful when you want to filter based on words or phrases. It extends basic search by allowing for advanced queries. You can access advanced search using the "Open Advanced Search" button shown in the image above. The advanced search can be applied to either files or descriptions.
![Advanced Search](/assets/images/docs/adv-search.png)

### Facets
Facets can be used to filter which entries are shown in the search results. For example, you can filter to only show tools from Docker Hub.

![Facets Search](/assets/images/docs/facets-search.png)

The verified, private access, and entry type facets are all exclusive facets. This means that only one of the options can be selected at a time. For all other facets, you can select multiple filters. For example, you could filter by multiple authors at the same time.

## Permalinks
When you filter the search with basic search, advanced search, or facets, a share button will become visible. This button will provide you with a permalink for sharing results with other people. Note that the page number is not yet included in the permalink.
![Permalinks](/assets/images/docs/permalink-search.png)

## File Format Search
You can also search by file format. This is helpful if you have a file of some type, and want to see which tools/workflows use it as an input file. Currently this is only supported for [CWL](https://www.commonwl.org/user_guide/16-file-formats/).
## Tag Cloud
The tag cloud shows a cloud of commonly used tags from all tools and workflows, depending on which tab you are on. It can be helpful for narrowing down your search.
![Tag Cloud](/assets/images/docs/tag-cloud.png)
