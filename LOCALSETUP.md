# Setting Up Cask Market Locally

You need to have some sort of webserver in your machine to serve from a directory. Using [MAMP](https://www.mamp.info/en/) will probably the fastest way to get started. Point your webserver to the cask-marketplace directory.


## Building Market
1. `cd` into the cask-marketplace repository
2. `cd packager`
3. `mvn clean package`
4. `cd ..`
5. `java -cp packager/target/*:packager/target/lib/* co.cask.marketplace.Tool build`

Windows
5. `java -cp .\packager\target\*;.\packager\target\lib\* co.cask.marketplace.Tool build`

## Start WebServer 
```python -m SimpleHTTPServer```

## Or Setting up MAMP
1. Launch MAMP. *If this is the first time you are launching MAMP, make sure you check Never Open This Dialog Again, and choose launch MAMP (not MAMP PRO).*
2. Open **Preferences**
3. Go to **Ports** Tab
4. Change **Apache Port** to 80
5. Go to **Web Server** Tab
6. Make sure Apache is selected
7. Click on the Folder icon with 3 dots next to Document Root.
8. Navigate your cask-marketplace directory, and click **Select**
9. Click **OK**
10. Click **Start Server**


## Changing Cask Market Basepath in CDAP

### For CDAP 4.0.x
Navigate to
```
<cdap sdk directory>/ui/server/config/cdap-ui-config.json
```

Modify the market property
```
"market": {
  "path": "http://localhost",
  "version": ""
}
```

Restart CDAP UI

### For CDAP 4.1.x and above
Modify `cdap-site.xml`

```
<property>
    <name>market.base.url</name>
    <value>http://localhost</value>
    <description>
      Local Cask Market base url
    </description>
</property>
```

Restart CDAP SDK
