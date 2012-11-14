import static org.vertx.groovy.core.streams.Pump.createPump
import org.vertx.groovy.core.http.RouteMatcher


def client = vertx.createHttpClient()
		.setPort(80).setHost("")
def filesRoot = System.getProperty("user.home")+"/.vertx/server"
def rm = new RouteMatcher()

rm.put('/upload/:path/:modId') { req ->
	def path = "${filesRoot}/${req.params['path']}"
	def filename = "${path}/${req.params['modId']}"

	vertx.fileSystem.mkdirSync(path, null, true)

	req.pause()
	vertx.fileSystem.open(filename) { ares ->
		def file = ares.result
		def pump = createPump(req, file.writeStream)
		req.endHandler {
			file.close {
				println "Uploaded ${pump.bytesPumped} bytes to $filename"
				req.response.end()
			}
		}
		pump.start()
		req.resume()
	}
}

rm.get('/vertx-mods/mods/:path/:modId') { req ->
	def fullPath = "${filesRoot}/${req.params['path']}/${req.params['modId']}"
	if(vertx.fileSystem.existsSync(fullPath)){
		req.response.sendFile fullPath
	} else{
		//req.response.end "Mod ${req.params['path']}/${req.params['modId']} not found"
		def c_req = client.request(req.method, req.uri) { c_res ->
			println "Proxying response: ${c_res.statusCode}"
			req.response.chunked = true
			req.response.statusCode = c_res.statusCode
			req.response.headers << c_res.headers
			c_res.dataHandler { data ->
				println "Proxying response body: $data"
				req.response << data
			}
			c_res.endHandler { req.response.end() }
		}
		c_req.chunked = true
		c_req.headers << req.headers
		req.dataHandler { data ->
			println "Proxying request body ${data}"
			c_req << data
		}
		req.endHandler{ c_req.end() }
	}
}

rm.noMatch{ req ->
	println "${req.uri} not matched"
	req.response.end "${req.uri} not matched"
}

vertx.createHttpServer().requestHandler(rm.asClosure()).listen(80,"localhost")
