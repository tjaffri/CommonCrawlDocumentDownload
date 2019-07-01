import json
from os.path import basename
import tld

INPUT_FILE_NAME = 'commoncrawl-CC-MAIN-2019-26.full.txt'

ALLOWED_DOMAINS = { "com", "edu", "org", "net" }
ALLOWED_STATUS_CODES = ['200']

MIME_TYPE_DOCX = 'officedocument.wordprocessingml'

with open(INPUT_FILE_NAME, encoding='utf-8') as file_handle:
        line_number = 0
        for line in file_handle:
            line_number += 1

            if line.isspace():
                continue
                
            try:
                crawl_item = json.loads(line)
                
                # apply status code filter
                if crawl_item['status'] not in ALLOWED_STATUS_CODES:
                    continue

                # get true filename (crawler even things foo.com/bar?something=a.docx is a docx which it may not always be)
                url = crawl_item['url']
                if '?' in url:
                    url = url[:url.find('?')]

                true_fname = basename(url)
                crawl_item['url_file_name'] = true_fname

                # if true filename is¥not docx and mime does not look like docx either, discard
                if not true_fname.endswith('.docx'):
                   if MIME_TYPE_DOCX not in crawl_item['mime-detected']:
                        continue # mispatch

                # apply tld filter
                try:
                    found_tld = tld.get_tld(crawl_item['url'])
                    if found_tld not in ALLOWED_DOMAINS:
                        continue
                except tld.exceptions.TldDomainNotFound:
                    continue # could not extract TLD, so disallow by default

                # emit original unparsed line if we made it this far
                print(line.strip())

            except json.decoder.JSONDecodeError:
                print(f'Invalid JSON at line: {line_number}')
                exit()
