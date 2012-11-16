import static org.vertx.groovy.core.streams.Pump.createPump
import org.vertx.groovy.core.http.RouteMatcher
import org.vertx.groovy.core.buffer.Buffer


def client = vertx.createHttpClient([host:"57.56.20.40",port: 3128])
def filesRoot = System.getProperty("user.home")+"/.vertx/server"
def rm = new RouteMatcher()

rm.put('/install/:path/:modId') { req ->
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

rm.get('/vertx-mods/mods/:path/:modId') {
	req ->
	def path = "${filesRoot}/${req.params['path']}"
	def fullPath = "${filesRoot}/${req.params['path']}/${req.params['modId']}"
	if(vertx.fileSystem.existsSync(fullPath)){
		req.response.sendFile fullPath
	} else{
		def clientRequest = client.request("GET", "http://vert-x.github.com/${req.uri}") { clientResponse ->
			if(clientResponse.statusCode == 200){
				def buffer = new Buffer()
				clientResponse.dataHandler {
					data -> buffer << data
				}
				clientResponse.endHandler{
					vertx.fileSystem.mkdirSync(path, null, true)
					vertx.fileSystem.open(fullPath) {ares ->
						def file = ares.result
						file.writeStream.writeBuffer(buffer)
						file.close(){
							println "closed"
							req.response.sendFile fullPath
						}
					}
				}
			}else{
				req.response.end "mod ${req.uri} not found"
			}
		}
		clientRequest.end()
	}
}

rm.noMatch{ req ->
	println "${req.uri} not matched"
	req.response.end "${req.uri} not matched"
}

vertx.createHttpServer().requestHandler(rm.asClosure()).listen(80,"localhost")
