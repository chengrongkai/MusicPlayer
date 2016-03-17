package app.musicplayer.util;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.AudioHeader;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import app.musicplayer.model.Library;
import app.musicplayer.model.Song;

public class DirectoryWatch {
	
	private WatchService watcher;
	private Map<WatchKey, Path> keys;
	
	private boolean trace = false;
	
	private String path;
	
	// Initializes the variable to store the number of files in library.xml file.
	// This is used to set the id of the new songs being added to library.xml
	private int xmlFileNum;
	
	// Array list with new songs to be added to library.xml
	private static ArrayList<Song> newSongs = new ArrayList<Song>();
	
	/**
	 * Creates a Directory Watch object.
	 */
	public DirectoryWatch(Path musicDirectory, int xmlFileNum) {
		// Sets the number of files in library.xml
		this.xmlFileNum = xmlFileNum;
		try {
			// Creates new watch service to monitor directory.
			watcher = FileSystems.getDefault().newWatchService();
			keys = new HashMap<WatchKey, Path>();
			
			// Registers music directory and all sub directories with watch service.
			registerAll(musicDirectory);
			
			// Enable trace after initial registration.
			this.trace = true;
			
			// TODO: DEBUG
			System.out.println("DW85_Watch Service reigstered for directory: " + musicDirectory.getFileName() + 
					" in: " + musicDirectory.getParent());
			
			// Sets infinite loop to monitor directory.
			while (true) {
				// Waits for the key to be signaled.
				WatchKey key;
				try {
					key = watcher.take();
				} catch (InterruptedException e) {
					e.printStackTrace();
					return;
				}
				
				Path dir = keys.get(key);
				if (dir == null) {
					System.err.println("Watchkey not recognized!");
					continue;
				}
				
				for (WatchEvent<?> event: key.pollEvents()) {
					// Gets event type (create, delete, modify).
					WatchEvent.Kind<?> kind = event.kind();
					
					// Gets file name of file that triggered event.
					@SuppressWarnings("unchecked")
					WatchEvent<Path> ev = (WatchEvent<Path>) event;
					Path fileName = ev.context();
					Path child = dir.resolve(fileName);

					// 	TODO: DEBUG
	                System.out.format("DW116_%s: %s\n", kind.name(), child);
					
					// If directory is created, register directory and sub directories with watch service.
	                // If file is created, creates a Song objects from the new file and adds it to the newSongs array list.
					if (kind == ENTRY_CREATE) {
						try {
							if (Files.isDirectory(child, NOFOLLOW_LINKS)) {
								// 	TODO: DEBUG
				                System.out.println("DW124_Before Register All");
								registerAll(child);
							} else if (child.toFile().isFile()) {
								// Adds the new created songs to the new songs array list.
								new Thread(() -> {
									// 	TODO: DEBUG
					                System.out.println("DW130_Before File Create");
									fileCreate(child);
									// 	TODO: DEBUG
					                System.out.println("DW133_After File Create");
								}).start();
								// 	TODO: DEBUG
				                System.out.println("DW136_After thread start");
							}
						} catch (IOException ex) {
							ex.printStackTrace();
						}
						// 	TODO: DEBUG
		                System.out.println("DW142_After try");
					} else if (kind == ENTRY_DELETE) {
						System.out.println("DW144_File deleted!");
					}
					// 	TODO: DEBUG
	                System.out.println("DW147_After If");
				}
				
				System.out.println("After foor loop.");
				
				// Resets the key.
				// If the key is no longer valid, exits loop.
				// Makes it possible to receive further watch events.
				boolean valid = key.reset();
				if (!valid) {
					keys.remove(key);
					// If all directories are inaccessible.
					if (keys.isEmpty()){
						break;
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public String getPath() {
		return this.path;
	}
	
	private void registerAll(final Path start) throws IOException {
		// Registers directory and all its sub-directories with the WatchService.
        Files.walkFileTree(start, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                register(dir);
                return FileVisitResult.CONTINUE;
            }
        });
	}
	
	private void register(Path dir) throws IOException {
        WatchKey key = dir.register(watcher, ENTRY_CREATE, ENTRY_DELETE);
        if (trace) {
            Path prev = keys.get(key);
            if (prev == null) {
            	// TODO: DEBUG
                System.out.format("DW190_Register: %s\n", dir);
            } else {
                if (!dir.equals(prev)) {
                	// TODO: DEBUG
                    System.out.format("DW194_Update: %s -> %s\n", prev, dir);
                }
            }
        }
        keys.put(key, dir);
	}
	
	private void fileCreate(Path filePath) {
		
		// TODO: DEBUG
		System.out.println("DW204_File Path: " + filePath);
		
		File file = filePath.toFile();

		// TODO: DEBUG
		System.out.println("DW209_File: " + file);
		
		// TODO: DEBUG
		System.out.println("DW212_New file created: " + file.getName());
		
		// Infinite loop to wait until file is not in use by another process.
		while (!file.renameTo(file)) {
			// TODO: DEBUG
			System.out.println("DW217_File in use: " + file.getName());
		}
		
        try {
        	
            AudioFile audioFile = AudioFileIO.read(file);
            Tag tag = audioFile.getTag();
            AudioHeader header = audioFile.getAudioHeader();
            
            // Gets song properties.
            int id = xmlFileNum++;
            String title = tag.getFirst(FieldKey.TITLE);
            // Gets the artist, empty string assigned if song has no artist.
            String artistTitle = tag.getFirst(FieldKey.ALBUM_ARTIST);
            if (artistTitle == null || artistTitle.equals("") || artistTitle.equals("null")) {
                artistTitle = tag.getFirst(FieldKey.ARTIST);
            }
            String artist = (artistTitle == null || artistTitle.equals("") || artistTitle.equals("null")) ? "" : artistTitle;
            String album = tag.getFirst(FieldKey.ALBUM);
            // Gets the track length (as an int), converts to long and saves it as a duration object.                
            Duration length = Duration.ofSeconds(Long.valueOf(header.getTrackLength()));
            // Gets the track number and converts to an int. Assigns 0 if a track number is null.
            String track = tag.getFirst(FieldKey.TRACK);                
            int trackNumber = Integer.parseInt((track == null || track.equals("") || track.equals("null")) ? "0" : track);
            // Gets disc number and converts to int. Assigns 0 if the disc number is null.
            String disc = tag.getFirst(FieldKey.DISC_NO);
            int discNumber = Integer.parseInt((disc == null || disc.equals("") || disc.equals("null")) ? "0" : disc);
            int playCount = 0;
            LocalDateTime playDate = LocalDateTime.now();
            String location = Paths.get(file.getAbsolutePath()).toString();
            
            // TODO: DEBUG
//            System.out.println("ID: " + id);
//            System.out.println("Title: " + title);
//            System.out.println("Artist: " + artist);
//            System.out.println("Album: " + album);
//            System.out.println("Length: " + length);
//            System.out.println("Track Number: " + trackNumber);
//            System.out.println("Disc Number: " + discNumber);
//            System.out.println("Play Count: " + playCount);
//            System.out.println("Play Date: " + playDate);
//            System.out.println("Location: " + location);
            
            // Creates a new song object for the added song and adds it to the newSongs array list.
            Song newSong = new Song(id, title, artist, album, length, trackNumber, discNumber, playCount, playDate, location);
            newSongs.add(newSong);
            
            // TODO: DEBUG
            System.out.println("DW265_New song added to newSongs: " + newSongs.get(0).getTitle());
            
            // Adds the new song to the xml file.
            editCreateXMLFile(newSong);
            
            // Updates the array lists containing songs, albums, and artists in the library.
            Library.updateSongsList();
            Library.updateAlbumsList();
            Library.updateArtistsList();

		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}
	
	// TODO: ADD NEW SONGS TO XML FILE AND TO SONGS ARRAY LIST IN LIBRARY
	private void editCreateXMLFile(Song song) {
		// TODO: DEBUG
		System.out.println("DW280_In editCreateXMLFile()");
		
        try {
			DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
			Document doc = docBuilder.parse(Resources.JAR + "library.xml");
			
            XPathFactory xPathfactory = XPathFactory.newInstance();
            XPath xpath = xPathfactory.newXPath();
            
            XPathExpression expr = xpath.compile("/library/songs");
            Node songsNode = ((NodeList) expr.evaluate(doc, XPathConstants.NODESET)).item(0);
            
            // Creates a new song element and its sub elements.
            Element newSong = doc.createElement("song");
            Element newSongId = doc.createElement("id");
            Element newSongTitle = doc.createElement("title");
            Element newSongArtist = doc.createElement("artist");
            Element newSongAlbum = doc.createElement("album");
            Element newSongLength = doc.createElement("length");
            Element newSongTrackNumber = doc.createElement("trackNumber");
            Element newSongDiscNumber = doc.createElement("discNumber");
            Element newSongPlayCount = doc.createElement("playCount");
            Element newSongPlayDate = doc.createElement("playDate");
            Element newSongLocation = doc.createElement("location");

            // Saves the new song data.
            newSongId.setTextContent(Integer.toString(song.getId()));
            newSongTitle.setTextContent(song.getTitle());
            newSongArtist.setTextContent(song.getArtist());
            newSongAlbum.setTextContent(song.getAlbum());
            newSongLength.setTextContent(Long.toString(song.getLengthInSeconds()));
            newSongTrackNumber.setTextContent(Integer.toString(song.getTrackNumber()));
            newSongDiscNumber.setTextContent(Integer.toString(song.getDiscNumber()));
            newSongPlayCount.setTextContent(Integer.toString(song.getPlayCount()));
            newSongPlayDate.setTextContent(song.getPlayDate().toString());
            newSongLocation.setTextContent(song.getLocation());
            
            // Adds the new song to the xml file.
            songsNode.appendChild(newSong);
            // Adds the new song data to the new song.
            newSong.appendChild(newSongId);
            newSong.appendChild(newSongTitle);
            newSong.appendChild(newSongArtist);
            newSong.appendChild(newSongAlbum);
            newSong.appendChild(newSongLength);
            newSong.appendChild(newSongTrackNumber);
            newSong.appendChild(newSongDiscNumber);
            newSong.appendChild(newSongPlayCount);
            newSong.appendChild(newSongPlayDate);
            newSong.appendChild(newSongLocation);
            
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            DOMSource source = new DOMSource(doc);
            File xmlFile = new File(Resources.JAR + "library.xml");
            StreamResult result = new StreamResult(xmlFile);
            transformer.transform(source, result);
            
		} catch (Exception ex) {
			// TODO Auto-generated catch block
			ex.printStackTrace();
		}
        
		// TODO: DEBUG
		System.out.println("DW347_End of editCreateXMLFile()");
	}
	
	private void editDeleteXMLFile() {}

}
